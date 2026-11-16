package com.interviewagent.orchestrator.service;

import com.interviewagent.orchestrator.client.AiServiceClient;
import com.interviewagent.orchestrator.dto.SessionDtos.AiMessagePayload;
import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.dto.SessionDtos.CreateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.MessageResponse;
import com.interviewagent.orchestrator.dto.SessionDtos.SessionResponse;
import com.interviewagent.orchestrator.entity.ChatMessageEntity;
import com.interviewagent.orchestrator.entity.InterviewSession;
import com.interviewagent.orchestrator.entity.InterviewStage;
import com.interviewagent.orchestrator.entity.MessageRole;
import com.interviewagent.orchestrator.repository.ChatMessageRepository;
import com.interviewagent.orchestrator.repository.InterviewSessionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class InterviewSessionService {

    public static final UUID DEFAULT_DEMO_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final InterviewSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiServiceClient aiServiceClient;

    public InterviewSessionService(
            InterviewSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            AiServiceClient aiServiceClient) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional
    public Mono<SessionResponse> createSession(CreateSessionRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        InterviewSession session = new InterviewSession();
        UUID userId = (request != null && request.userId() != null)
                ? request.userId()
                : DEFAULT_DEMO_USER_ID;
        String targetRole = (request != null
                        && request.targetRole() != null
                        && !request.targetRole().isBlank())
                ? request.targetRole().trim()
                : "Software Engineer";

        session.setUserId(userId);
        if (request != null && request.resumeId() != null) {
            session.setResumeId(request.resumeId());
        }
        session.setTargetRole(targetRole);
        session.setStage(InterviewStage.INTRO);
        session.setTurnCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        return sessionRepository
                .save(session)
                .flatMap(savedSession -> {
                    ChatMessageEntity systemMsg = new ChatMessageEntity();
                    systemMsg.setSessionId(savedSession.getId());
                    systemMsg.setRole(MessageRole.SYSTEM);
                    systemMsg.setStage(InterviewStage.INTRO);
                    systemMsg.setContent(buildSystemPrompt(targetRole));
                    systemMsg.setCreatedAt(OffsetDateTime.now());

                    return messageRepository
                            .save(systemMsg)
                            .thenReturn(SessionResponse.fromEntity(savedSession));
                });
    }

    public Mono<SessionResponse> getSession(UUID sessionId) {
        return sessionRepository
                .findById(sessionId)
                .map(SessionResponse::fromEntity)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Interview session not found: " + sessionId)));
    }

    public Mono<List<MessageResponse>> getMessages(UUID sessionId) {
        return sessionRepository
                .existsById(sessionId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Interview session not found: " + sessionId));
                    }
                    return messageRepository
                            .findBySessionIdOrderByCreatedAtAsc(sessionId)
                            .map(MessageResponse::fromEntity)
                            .collectList();
                });
    }

    public Flux<ServerSentEvent<AiStreamChunk>> streamCandidateMessage(
            UUID sessionId, String userContent) {
        return sessionRepository
                .findById(sessionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Interview session not found: " + sessionId)))
                .flatMap(session -> {
                    ChatMessageEntity userMessage = new ChatMessageEntity();
                    userMessage.setSessionId(sessionId);
                    userMessage.setRole(MessageRole.USER);
                    userMessage.setStage(session.getStage());
                    userMessage.setContent(userContent);
                    userMessage.setCreatedAt(OffsetDateTime.now());

                    session.setTurnCount(session.getTurnCount() + 1);
                    session.setUpdatedAt(OffsetDateTime.now());

                    return messageRepository
                            .save(userMessage)
                            .then(sessionRepository.save(session));
                })
                .flatMapMany(session -> messageRepository
                        .findBySessionIdOrderByCreatedAtAsc(sessionId)
                        .map(msg -> new AiMessagePayload(
                                msg.getRole().name().toLowerCase(Locale.ROOT),
                                msg.getContent()))
                        .collectList()
                        .flatMapMany(history -> {
                            StringBuilder assistantBuffer = new StringBuilder();

                            Flux<AiStreamChunk> tokenStream = aiServiceClient
                                    .streamChat(history)
                                    .doOnNext(chunk -> {
                                        if (chunk.token() != null) {
                                            assistantBuffer.append(chunk.token());
                                        }
                                    });

                            Mono<Void> persistAssistantMono = Mono.defer(() -> {
                                String fullReply = assistantBuffer.toString();
                                if (fullReply.isEmpty()) {
                                    return Mono.empty();
                                }
                                ChatMessageEntity assistantMessage = new ChatMessageEntity();
                                assistantMessage.setSessionId(sessionId);
                                assistantMessage.setRole(MessageRole.ASSISTANT);
                                assistantMessage.setStage(session.getStage());
                                assistantMessage.setContent(fullReply);
                                assistantMessage.setCreatedAt(OffsetDateTime.now());
                                return messageRepository.save(assistantMessage).then();
                            });

                            return tokenStream
                                    .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                                            .data(chunk)
                                            .build())
                                    .concatWith(persistAssistantMono.then(Mono.empty()));
                        }));
    }

    private static String buildSystemPrompt(String targetRole) {
        return "You are an experienced technical interviewer conducting a structured interview for a "
                + targetRole
                + " position. Ask one clear question at a time, keep responses concise, and probe into the candidate's reasoning.";
    }
}
