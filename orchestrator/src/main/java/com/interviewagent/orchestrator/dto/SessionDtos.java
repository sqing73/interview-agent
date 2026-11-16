package com.interviewagent.orchestrator.dto;

import com.interviewagent.orchestrator.entity.ChatMessageEntity;
import com.interviewagent.orchestrator.entity.InterviewSession;
import com.interviewagent.orchestrator.entity.InterviewStage;
import com.interviewagent.orchestrator.entity.MessageRole;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {
    }

    public record CreateSessionRequest(
            UUID userId,
            UUID resumeId,
            String targetRole) {
    }

    public record SessionResponse(
            UUID id,
            UUID userId,
            UUID resumeId,
            String targetRole,
            InterviewStage stage,
            int turnCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static SessionResponse fromEntity(InterviewSession session) {
            return new SessionResponse(
                    session.getId(),
                    session.getUserId(),
                    session.getResumeId(),
                    session.getTargetRole(),
                    session.getStage(),
                    session.getTurnCount(),
                    session.getCreatedAt(),
                    session.getUpdatedAt());
        }
    }

    public record SendMessageRequest(
            @NotBlank(message = "Message content must not be blank")
            String content) {
    }

    public record MessageResponse(
            UUID id,
            UUID sessionId,
            MessageRole role,
            InterviewStage stage,
            String content,
            OffsetDateTime createdAt) {

        public static MessageResponse fromEntity(ChatMessageEntity entity) {
            return new MessageResponse(
                    entity.getId(),
                    entity.getSessionId(),
                    entity.getRole(),
                    entity.getStage(),
                    entity.getContent(),
                    entity.getCreatedAt());
        }
    }

    public record AiMessagePayload(String role, String content) {
    }

    public record AiGenerateStreamRequest(List<AiMessagePayload> messages) {
    }

    public record AiStreamChunk(String token, Boolean done, String error) {
    }
}
