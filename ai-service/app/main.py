"""FastAPI application exposing OpenAPI docs and SSE streaming endpoints."""

from collections.abc import AsyncIterator

from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
import httpx

from app.config import settings
from app.ollama_client import (
    check_ollama_health,
    evaluate_session_with_ollama,
    parse_resume_with_ollama,
    stream_ollama_chat,
)
from app.prompts import build_stage_system_prompt
from app.schemas import (
    ChatMessage,
    EvaluateSessionRequest,
    EvaluateSessionResponse,
    GenerateStreamRequest,
    HealthResponse,
    ParseResumeRequest,
    ParseResumeResponse,
    StreamChunk,
)

app = FastAPI(
    title=settings.app_title,
    version=settings.app_version,
    description="Streaming LLM inference gateway backed by local Ollama.",
)


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health() -> HealthResponse:
    """Check service liveness and Ollama backend connectivity."""
    reachable = await check_ollama_health()
    return HealthResponse(
        status="ok" if reachable else "degraded",
        ollama_url=settings.ollama_base_url,
        model=settings.ollama_model,
        ollama_reachable=reachable,
    )


@app.post(
    "/api/v1/generate-stream",
    tags=["inference"],
    summary="Stream LLM response tokens over SSE",
    responses={
        200: {
            "description": "Server-Sent Events stream of StreamChunk payloads.",
            "model": StreamChunk,
            "content": {"text/event-stream": {}},
        }
    },
)
async def generate_stream(request: GenerateStreamRequest) -> StreamingResponse:
    """Stream generated tokens from Ollama as Server-Sent Events (SSE)."""
    messages = list(request.messages)
    if request.stage is not None:
        system_prompt = build_stage_system_prompt(
            stage=request.stage,
            target_role=request.target_role,
            resume_skills=request.resume_skills,
            resume_summary=request.resume_summary,
        )
        if request.stage == "COMPLETED":
            messages = [
                ChatMessage(role="system", content=system_prompt),
                ChatMessage(
                    role="user",
                    content="The interview is complete. Please leave a brief closing thank-you note.",
                ),
            ]
        else:
            non_system_messages = [m for m in messages if m.role != "system"]
            messages = [
                ChatMessage(role="system", content=system_prompt),
                *non_system_messages,
            ]

    async def event_generator() -> AsyncIterator[str]:
        try:
            async for token in stream_ollama_chat(
                messages=messages,
                model=request.model,
                temperature=request.temperature,
            ):
                yield StreamChunk(token=token).to_sse()
            yield StreamChunk(done=True).to_sse()
        except (httpx.HTTPError, RuntimeError) as exc:
            yield StreamChunk(error=str(exc)).to_sse(event="error")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post(
    "/api/v1/parse-resume",
    response_model=ParseResumeResponse,
    tags=["resume"],
    summary="Extract structured skills and summary from raw resume text",
)
async def parse_resume(request: ParseResumeRequest) -> ParseResumeResponse:
    """Parse raw resume text into structured skills and experience summary via Ollama."""
    try:
        return await parse_resume_with_ollama(
            raw_text=request.raw_text,
            model=request.model,
        )
    except (httpx.HTTPError, RuntimeError, ValueError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Resume parsing failed: {exc}",
        ) from exc


@app.post(
    "/api/v1/evaluate-session",
    response_model=EvaluateSessionResponse,
    tags=["evaluation"],
    summary="Evaluate a completed interview transcript and return a structured scorecard",
)
async def evaluate_session(
    request: EvaluateSessionRequest,
) -> EvaluateSessionResponse:
    """Grade the candidate's interview transcript via Ollama JSON mode."""
    try:
        return await evaluate_session_with_ollama(request)
    except (httpx.HTTPError, RuntimeError, ValueError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Session evaluation failed: {exc}",
        ) from exc
