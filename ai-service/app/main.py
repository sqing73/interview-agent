"""FastAPI application exposing OpenAPI docs and SSE streaming endpoints."""

from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import httpx

from app.config import settings
from app.ollama_client import check_ollama_health, stream_ollama_chat
from app.schemas import GenerateStreamRequest, HealthResponse, StreamChunk

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

    async def event_generator() -> AsyncIterator[str]:
        try:
            async for token in stream_ollama_chat(
                messages=request.messages,
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
