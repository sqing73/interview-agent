"""Async client for streaming chat completions from Ollama."""

from collections.abc import AsyncIterator
import json

import httpx

from app.config import settings
from app.prompts import RESUME_PARSE_SYSTEM_PROMPT, build_evaluation_system_prompt
from app.schemas import (
    ChatMessage,
    EvaluateSessionRequest,
    EvaluateSessionResponse,
    OllamaChatRequest,
    OllamaOptions,
    ParseResumeResponse,
)


def _build_chat_timeout() -> httpx.Timeout:
    """Build standard HTTP timeout configuration for Ollama chat calls."""
    return httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_timeout_seconds,
        write=settings.ollama_connect_timeout_seconds,
        pool=settings.ollama_connect_timeout_seconds,
    )


async def check_ollama_health(base_url: str | None = None) -> bool:
    """Return True if the Ollama server responds to GET /api/tags."""
    target_url = (base_url or settings.ollama_base_url).rstrip("/")
    try:
        async with httpx.AsyncClient(
            timeout=settings.ollama_health_timeout_seconds
        ) as client:
            response = await client.get(f"{target_url}/api/tags")
            return response.status_code == 200
    except httpx.HTTPError:
        return False


async def stream_ollama_chat(
    messages: list[ChatMessage],
    model: str | None = None,
    temperature: float = settings.default_chat_temperature,
) -> AsyncIterator[str]:
    """Stream text tokens from Ollama's /api/chat NDJSON endpoint."""
    target_url = settings.ollama_base_url.rstrip("/")
    request_payload = OllamaChatRequest(
        model=model or settings.ollama_model,
        messages=messages,
        stream=True,
        options=OllamaOptions(temperature=temperature),
    ).model_dump(exclude_none=True)

    async with httpx.AsyncClient(timeout=_build_chat_timeout()) as client:
        async with client.stream(
            "POST",
            f"{target_url}/api/chat",
            json=request_payload,
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line:
                    continue
                chunk = json.loads(line)
                if chunk.get("error"):
                    raise RuntimeError(chunk["error"])
                content = chunk.get("message", {}).get("content", "")
                if content:
                    yield content
                if chunk.get("done"):
                    break


async def parse_resume_with_ollama(
    raw_text: str,
    model: str | None = None,
) -> ParseResumeResponse:
    """Extract structured skills and summary from resume text using Ollama JSON mode."""
    target_url = settings.ollama_base_url.rstrip("/")
    request_payload = OllamaChatRequest(
        model=model or settings.ollama_model,
        messages=[
            ChatMessage(role="system", content=RESUME_PARSE_SYSTEM_PROMPT),
            ChatMessage(role="user", content=raw_text),
        ],
        stream=False,
        format="json",
        options=OllamaOptions(
            temperature=settings.resume_parse_temperature,
            repeat_penalty=settings.ollama_repeat_penalty,
        ),
    ).model_dump(exclude_none=True)

    async with httpx.AsyncClient(timeout=_build_chat_timeout()) as client:
        response = await client.post(
            f"{target_url}/api/chat",
            json=request_payload,
        )
        response.raise_for_status()
        body = response.json()

    if body.get("error"):
        raise RuntimeError(body["error"])

    content = body.get("message", {}).get("content", "{}")
    parsed = json.loads(content)
    skills = [
        str(s).strip()
        for s in parsed.get("skills", [])
        if str(s).strip()
    ]
    summary = str(parsed.get("experience_summary", "")).strip()
    return ParseResumeResponse(skills=skills, experience_summary=summary)


def _clamp_score(value: object, default: int = 75) -> int:
    """Safely convert a parsed JSON value into an integer clamped to [0, 100]."""
    try:
        numeric = int(float(str(value)))
        return max(0, min(100, numeric))
    except (TypeError, ValueError):
        return default


async def evaluate_session_with_ollama(
    request: EvaluateSessionRequest,
) -> EvaluateSessionResponse:
    """Evaluate an interview transcript using Ollama JSON mode and return a scorecard."""
    target_url = settings.ollama_base_url.rstrip("/")
    system_prompt = build_evaluation_system_prompt(
        target_role=request.target_role,
        resume_skills=request.resume_skills,
        resume_summary=request.resume_summary,
    )

    transcript_lines: list[str] = []
    for msg in request.messages:
        if msg.role == "system":
            continue
        speaker = "Candidate" if msg.role == "user" else "Interviewer"
        transcript_lines.append(f"{speaker}: {msg.content}")

    transcript_text = (
        "\n\n".join(transcript_lines)
        if transcript_lines
        else "No conversation transcript recorded."
    )

    request_payload = OllamaChatRequest(
        model=request.model or settings.ollama_model,
        messages=[
            ChatMessage(role="system", content=system_prompt),
            ChatMessage(
                role="user",
                content=(
                    f"Evaluate the following {request.target_role} interview transcript:\n\n"
                    f"{transcript_text}"
                ),
            ),
        ],
        stream=False,
        format="json",
        options=OllamaOptions(
            temperature=settings.resume_parse_temperature,
            repeat_penalty=settings.ollama_repeat_penalty,
        ),
    ).model_dump(exclude_none=True)

    async with httpx.AsyncClient(timeout=_build_chat_timeout()) as client:
        response = await client.post(
            f"{target_url}/api/chat",
            json=request_payload,
        )
        response.raise_for_status()
        body = response.json()

    if body.get("error"):
        raise RuntimeError(body["error"])

    content = body.get("message", {}).get("content", "{}")
    parsed = json.loads(content)

    technical = _clamp_score(parsed.get("technical_score"), 75)
    communication = _clamp_score(parsed.get("communication_score"), 75)
    problem_solving = _clamp_score(parsed.get("problem_solving_score"), 75)
    default_overall = round((technical + communication + problem_solving) / 3)
    overall = _clamp_score(parsed.get("overall_score"), default_overall)

    raw_rec = str(parsed.get("recommendation", "LEAN_HIRE")).upper().strip()
    valid_recs = {"STRONG_HIRE", "HIRE", "LEAN_HIRE", "NO_HIRE"}
    recommendation = raw_rec if raw_rec in valid_recs else "LEAN_HIRE"

    strengths = [
        str(item).strip()
        for item in parsed.get("strengths", [])
        if str(item).strip()
    ]
    improvements = [
        str(item).strip()
        for item in parsed.get("areas_for_improvement", [])
        if str(item).strip()
    ]
    summary = str(parsed.get("summary", "")).strip()

    return EvaluateSessionResponse(
        technical_score=technical,
        communication_score=communication,
        problem_solving_score=problem_solving,
        overall_score=overall,
        recommendation=recommendation,  # type: ignore[arg-type]
        strengths=strengths,
        areas_for_improvement=improvements,
        summary=summary,
    )
