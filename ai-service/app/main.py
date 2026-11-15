"""FastAPI application exposing OpenAPI docs and SSE streaming endpoints."""

from collections.abc import AsyncIterator
import json

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import httpx

from app.config import settings
from app.ollama_client import check_ollama_health, stream_ollama_chat
from app.schemas import GenerateStreamRequest, HealthResponse

app = FastAPI(
    title=settings.app_title,
    version=settings.app_version,
    description="Streaming LLM inference gateway backed by local Ollama.",
)


def _format_sse(data: dict[str, object], event: str | None = None) -> str:
    """Format a JSON payload as a Server-Sent Events frame."""
    prefix = f"event: {event}\n" if event else ""
    return f"{prefix}data: {json.dumps(data)}\n\n"


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
            "description": "Server-Sent Events stream of generated tokens.",
            "content": {"text/event-stream": {}},
        }
    },
)
async def generate_stream(request: GenerateStreamRequest) -> StreamingResponse:
    """Stream generated tokens from Ollama as Server-Sent Events (SSE)."""

    async def event_generator() -> AsyncIterator[str]:
        try:
            async for token in stream_ollama_chat(
                messages=request.messages,
                model=request.model,
                temperature=request.temperature,
            ):
                yield _format_sse({"token": token})
            yield _format_sse({"done": True})
        except (httpx.HTTPError, RuntimeError) as exc:
            yield _format_sse({"error": str(exc)}, event="error")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
