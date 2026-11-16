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


class StreamChunk(BaseModel):
    """Payload inside each Server-Sent Events (SSE) `data:` frame.

    Mirrors Java's `AiStreamChunk(String token, Boolean done, String error)`.
    """

    token: str | None = Field(
        default=None,
        description="Next generated text token from the LLM.",
    )
    done: bool | None = Field(
        default=None,
        description="True on the final frame when generation completes.",
    )
    error: str | None = Field(
        default=None,
        description="Error message if upstream LLM generation failed.",
    )

    def to_sse(self, event: Literal["error"] | None = None) -> str:
        """Serialize this chunk as a formatted SSE frame."""
        prefix = f"event: {event}\n" if event else ""
        return f"{prefix}data: {self.model_dump_json(exclude_none=True)}\n\n"
