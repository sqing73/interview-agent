"""Async client for streaming chat completions from Ollama."""

from collections.abc import AsyncIterator
import json

import httpx

from app.config import settings
from app.prompts import RESUME_PARSE_SYSTEM_PROMPT
from app.schemas import ChatMessage, ParseResumeResponse


async def check_ollama_health(base_url: str | None = None) -> bool:
    """Return True if the Ollama server responds to GET /api/tags."""
    target_url = (base_url or settings.ollama_base_url).rstrip("/")
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{target_url}/api/tags")
            return response.status_code == 200
    except httpx.HTTPError:
        return False


async def stream_ollama_chat(
    messages: list[ChatMessage],
    model: str | None = None,
    temperature: float = 0.7,
) -> AsyncIterator[str]:
    """Stream text tokens from Ollama's /api/chat NDJSON endpoint."""
    target_url = settings.ollama_base_url.rstrip("/")
    payload = {
        "model": model or settings.ollama_model,
        "messages": [msg.model_dump() for msg in messages],
        "stream": True,
        "options": {"temperature": temperature},
    }

    timeout = httpx.Timeout(
        connect=10.0,
        read=settings.ollama_timeout_seconds,
        write=10.0,
        pool=10.0,
    )

    async with httpx.AsyncClient(timeout=timeout) as client:
        async with client.stream(
            "POST",
            f"{target_url}/api/chat",
            json=payload,
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
    payload = {
        "model": model or settings.ollama_model,
        "messages": [
            {"role": "system", "content": RESUME_PARSE_SYSTEM_PROMPT},
            {"role": "user", "content": raw_text},
        ],
        "stream": False,
        "format": "json",
        "options": {"temperature": 0.1},
    }

    timeout = httpx.Timeout(
        connect=10.0,
        read=settings.ollama_timeout_seconds,
        write=10.0,
        pool=10.0,
    )

    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(f"{target_url}/api/chat", json=payload)
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
