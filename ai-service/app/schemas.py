"""Pydantic request and response schemas for OpenAPI documentation and validation."""

from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """A single turn in a conversation."""

    role: Literal["system", "user", "assistant"] = Field(
        ...,
        description="The speaker role for this message.",
    )
    content: str = Field(
        ...,
        min_length=1,
        description="The text content of the message.",
    )


class GenerateStreamRequest(BaseModel):
    """Request payload for streaming chat generation."""

    messages: list[ChatMessage] = Field(
        ...,
        min_length=1,
        description="Ordered conversation history sent to the LLM.",
        examples=[
            [
                {"role": "system", "content": "You are a technical interviewer."},
                {"role": "user", "content": "Ask me one Java concurrency question."},
            ]
        ],
    )
    model: str | None = Field(
        default=None,
        description="Optional Ollama model override; defaults to configured OLLAMA_MODEL.",
    )
    temperature: float = Field(
        default=0.7,
        ge=0.0,
        le=2.0,
        description="Sampling temperature for generation.",
    )


class HealthResponse(BaseModel):
    """Service health status including Ollama reachability."""

    status: Literal["ok", "degraded"]
    ollama_url: str
    model: str
    ollama_reachable: bool
