"""Tests for the FastAPI inference service."""

from collections.abc import AsyncIterator
import json

import httpx
import pytest

from app import main as app_main


@pytest.mark.asyncio
async def test_health_ok(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_health() -> bool:
        return True

    monkeypatch.setattr(app_main, "check_ollama_health", fake_health)

    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["ollama_reachable"] is True


@pytest.mark.asyncio
async def test_generate_stream_sse(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_stream(
        messages: list,
        model: str | None = None,
        temperature: float = 0.7,
    ) -> AsyncIterator[str]:
        for tok in ["Hello", " world", "!"]:
            yield tok

    monkeypatch.setattr(app_main, "stream_ollama_chat", fake_stream)

    payload = {
        "messages": [{"role": "user", "content": "Say hello"}],
    }
    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post("/api/v1/generate-stream", json=payload)

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")

    events = [
        json.loads(line.removeprefix("data: "))
        for line in response.text.splitlines()
        if line.startswith("data: ")
    ]
    assert events == [
        {"token": "Hello"},
        {"token": " world"},
        {"token": "!"},
        {"done": True},
    ]
