"""Configuration settings for the AI inference service."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables or .env."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_title: str = "AI Interview Agent - Inference Service"
    app_version: str = "0.1.0"
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:7b"
    ollama_timeout_seconds: float = 120.0
    ollama_connect_timeout_seconds: float = 10.0
    ollama_health_timeout_seconds: float = 5.0

    default_chat_temperature: float = 0.7
    resume_parse_temperature: float = 0.1
    ollama_repeat_penalty: float = 1.15
    default_target_role: str = "Software Engineer"


settings = Settings()
