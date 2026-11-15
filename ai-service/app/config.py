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


settings = Settings()
