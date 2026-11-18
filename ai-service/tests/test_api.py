"""Tests for the FastAPI inference service."""

from collections.abc import AsyncIterator
import json

import httpx
import pytest

from app import main as app_main
from app.schemas import (
    EvaluateSessionRequest,
    EvaluateSessionResponse,
    ParseResumeResponse,
)


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


@pytest.mark.asyncio
async def test_generate_stream_injects_stage_prompt(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured_messages: list = []

    async def fake_stream(
        messages: list,
        model: str | None = None,
        temperature: float = 0.7,
    ) -> AsyncIterator[str]:
        captured_messages.extend(messages)
        yield "Question?"

    monkeypatch.setattr(app_main, "stream_ollama_chat", fake_stream)

    payload = {
        "stage": "TECHNICAL",
        "target_role": "Backend Engineer",
        "resume_skills": ["Java 21", "Spring WebFlux"],
        "resume_summary": "3 years of reactive Java development.",
        "messages": [{"role": "user", "content": "Ready for technical round"}],
    }
    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post("/api/v1/generate-stream", json=payload)

    assert response.status_code == 200
    assert len(captured_messages) == 2
    assert captured_messages[0].role == "system"
    assert "TECHNICAL DEEP-DIVE" in captured_messages[0].content
    assert "Java 21, Spring WebFlux" in captured_messages[0].content
    assert captured_messages[1].role == "user"


@pytest.mark.asyncio
async def test_parse_resume_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_parse(
        raw_text: str,
        model: str | None = None,
    ) -> ParseResumeResponse:
        assert "Spring WebFlux" in raw_text
        return ParseResumeResponse(
            skills=["Java 21", "Spring WebFlux", "PostgreSQL"],
            experience_summary="Backend engineer specializing in reactive Java systems.",
        )

    monkeypatch.setattr(app_main, "parse_resume_with_ollama", fake_parse)

    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/api/v1/parse-resume",
            json={"raw_text": "Experienced in Java 21, Spring WebFlux, and PostgreSQL."},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["skills"] == ["Java 21", "Spring WebFlux", "PostgreSQL"]
    assert "reactive Java" in body["experience_summary"]


@pytest.mark.asyncio
async def test_evaluate_session_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_evaluate(
        request: EvaluateSessionRequest,
    ) -> EvaluateSessionResponse:
        assert request.target_role == "Backend Engineer"
        assert len(request.messages) == 2
        return EvaluateSessionResponse(
            technical_score=88,
            communication_score=92,
            problem_solving_score=85,
            overall_score=88,
            recommendation="STRONG_HIRE",
            strengths=["Deep understanding of Project Reactor backpressure"],
            areas_for_improvement=["Discuss database connection pooling metrics"],
            summary="Strong reactive backend engineer with clear communication.",
        )

    monkeypatch.setattr(app_main, "evaluate_session_with_ollama", fake_evaluate)

    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/api/v1/evaluate-session",
            json={
                "target_role": "Backend Engineer",
                "resume_skills": ["Java 21", "Spring WebFlux"],
                "messages": [
                    {"role": "assistant", "content": "How does WebFlux handle backpressure?"},
                    {"role": "user", "content": "Using Reactive Streams demand signaling."},
                ],
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["overall_score"] == 88
    assert body["recommendation"] == "STRONG_HIRE"
    assert len(body["strengths"]) == 1


@pytest.mark.asyncio
async def test_generate_stream_completed_uses_clean_thank_you_prompt(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured_messages: list = []

    async def fake_stream(
        messages: list,
        model: str | None = None,
        temperature: float = 0.7,
    ) -> AsyncIterator[str]:
        captured_messages.extend(messages)
        yield "Thank you for your time!"

    monkeypatch.setattr(app_main, "stream_ollama_chat", fake_stream)

    payload = {
        "stage": "COMPLETED",
        "target_role": "Backend Engineer",
        "resume_skills": ["Java 21"],
        "messages": [
            {"role": "assistant", "content": "Tell me about a STAR situation."},
            {"role": "user", "content": "..."},
        ],
    }
    transport = httpx.ASGITransport(app=app_main.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post("/api/v1/generate-stream", json=payload)

    assert response.status_code == 200
    assert len(captured_messages) == 2
    assert captured_messages[0].role == "system"
    assert "Thank the candidate warmly" in captured_messages[0].content
    assert "STAR" not in captured_messages[1].content
