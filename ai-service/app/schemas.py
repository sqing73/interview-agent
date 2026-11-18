"""Pydantic request and response schemas for OpenAPI documentation and validation."""

from typing import Literal

from pydantic import BaseModel, Field

from app.config import settings
from app.prompts import InterviewStage


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
        default=settings.default_chat_temperature,
        ge=0.0,
        le=2.0,
        description="Sampling temperature for generation.",
    )
    stage: InterviewStage | None = Field(
        default=None,
        description="Optional interview stage; when set, injects stage system prompt.",
    )
    target_role: str = Field(
        default=settings.default_target_role,
        description="Target job role for stage prompt rendering.",
    )
    resume_skills: list[str] | None = Field(
        default=None,
        description="Extracted candidate skills injected into the stage prompt.",
    )
    resume_summary: str | None = Field(
        default=None,
        description="Short candidate experience summary injected into the stage prompt.",
    )


class ParseResumeRequest(BaseModel):
    """Request payload for extracting structured skills from raw resume text."""

    raw_text: str = Field(
        ...,
        min_length=10,
        description="Plain text extracted from the candidate's uploaded resume.",
    )
    model: str | None = Field(
        default=None,
        description="Optional Ollama model override.",
    )


class ParseResumeResponse(BaseModel):
    """Structured skills and summary extracted from a resume."""

    skills: list[str] = Field(
        default_factory=list,
        description="List of technical and domain skills extracted from the resume.",
    )
    experience_summary: str = Field(
        default="",
        description="Concise 1-2 sentence summary of the candidate's experience.",
    )


class EvaluateSessionRequest(BaseModel):
    """Request payload for post-interview transcript evaluation."""

    messages: list[ChatMessage] = Field(
        ...,
        min_length=1,
        description="Full ordered conversation transcript to evaluate.",
    )
    target_role: str = Field(
        default=settings.default_target_role,
        description="Target job role the candidate interviewed for.",
    )
    resume_skills: list[str] | None = Field(
        default=None,
        description="Extracted candidate resume skills for context.",
    )
    resume_summary: str | None = Field(
        default=None,
        description="Extracted candidate resume summary for context.",
    )
    model: str | None = Field(
        default=None,
        description="Optional Ollama model override.",
    )


class EvaluateSessionResponse(BaseModel):
    """Structured evaluation scorecard returned after an interview ends."""

    technical_score: int = Field(
        ...,
        ge=0,
        le=100,
        description="Technical depth score (0-100).",
    )
    communication_score: int = Field(
        ...,
        ge=0,
        le=100,
        description="Communication clarity & structure score (0-100).",
    )
    problem_solving_score: int = Field(
        ...,
        ge=0,
        le=100,
        description="Problem-solving and analytical reasoning score (0-100).",
    )
    overall_score: int = Field(
        ...,
        ge=0,
        le=100,
        description="Weighted overall interview score (0-100).",
    )
    recommendation: Literal["STRONG_HIRE", "HIRE", "LEAN_HIRE", "NO_HIRE"] = Field(
        default="LEAN_HIRE",
        description="Hiring committee recommendation tier.",
    )
    strengths: list[str] = Field(
        default_factory=list,
        description="Key strengths demonstrated during the interview.",
    )
    areas_for_improvement: list[str] = Field(
        default_factory=list,
        description="Specific gaps or areas for improvement.",
    )
    summary: str = Field(
        default="",
        description="2-3 sentence executive summary of the candidate's performance.",
    )


class OllamaOptions(BaseModel):
    """Sampling options sent inside an Ollama /api/chat request."""

    temperature: float = Field(
        default=settings.default_chat_temperature,
        ge=0.0,
        le=2.0,
    )
    repeat_penalty: float = Field(
        default=settings.ollama_repeat_penalty,
        ge=0.0,
        le=2.0,
    )


class OllamaChatRequest(BaseModel):
    """Upstream request payload for Ollama POST /api/chat."""

    model: str
    messages: list[ChatMessage]
    stream: bool
    format: Literal["json"] | None = None
    options: OllamaOptions


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
