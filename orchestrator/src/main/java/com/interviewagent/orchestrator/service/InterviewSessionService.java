package com.interviewagent.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewagent.orchestrator.client.AiServiceClient;
import com.interviewagent.orchestrator.config.InterviewStageProperties;
import com.interviewagent.orchestrator.dto.ResumeDtos.StoredResumeMetadata;
import com.interviewagent.orchestrator.dto.SessionDtos.AiEvaluateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiGenerateStreamRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiMessagePayload;
import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.dto.SessionDtos.CreateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.EvaluationReport;
import com.interviewagent.orchestrator.dto.SessionDtos.MessageResponse;
import com.interviewagent.orchestrator.dto.SessionDtos.SessionResponse;
import com.interviewagent.orchestrator.entity.ChatMessageEntity;
import com.interviewagent.orchestrator.entity.InterviewSession;
import com.interviewagent.orchestrator.entity.InterviewStage;
import com.interviewagent.orchestrator.entity.MessageRole;
import com.interviewagent.orchestrator.repository.ChatMessageRepository;
import com.interviewagent.orchestrator.repository.InterviewSessionRepository;
import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class InterviewSessionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewSessionService.class);

    public static final UUID DEFAULT_DEMO_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final InterviewSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ResumeService resumeService;
    private final AiServiceClient aiServiceClient;
    private final InterviewStageProperties stageProperties;
    private final ObjectMapper objectMapper;

    public InterviewSessionService(
            InterviewSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            ResumeService resumeService,
            AiServiceClient aiServiceClient,
            InterviewStageProperties stageProperties) {
        this(
                sessionRepository,
                messageRepository,
                resumeService,
                aiServiceClient,
                stageProperties,
                new ObjectMapper());
    }

    @Autowired
    public InterviewSessionService(
            InterviewSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            ResumeService resumeService,
            AiServiceClient aiServiceClient,
            InterviewStageProperties stageProperties,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.resumeService = resumeService;
        this.aiServiceClient = aiServiceClient;
        this.stageProperties = stageProperties;
        this.objectMapper = objectMapper;
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
                .map(this::toSessionResponse);
    }

    public Mono<SessionResponse> getSession(UUID sessionId) {
        return sessionRepository
                .findById(sessionId)
                .map(this::toSessionResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Interview session not found: " + sessionId)));
    }

    @Transactional
    public Mono<SessionResponse> completeSession(UUID sessionId) {
        return sessionRepository
                .findById(sessionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Interview session not found: " + sessionId)))
                .flatMap(session -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    session.setStage(InterviewStage.COMPLETED);
                    session.setUpdatedAt(now);
                    if (session.getCompletedAt() == null) {
                        session.setCompletedAt(now);
                    }
                    return sessionRepository.save(session);
                })
                .doOnSuccess(saved -> triggerAsyncEvaluation(saved.getId()))
                .map(this::toSessionResponse);
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
                    int nextTurnCount = session.getTurnCount() + 1;
                    InterviewStage activeStage = resolveNextStage(
                            session.getStage(), nextTurnCount);

                    ChatMessageEntity userMessage = new ChatMessageEntity();
                    userMessage.setSessionId(sessionId);
                    userMessage.setRole(MessageRole.USER);
                    userMessage.setStage(activeStage);
                    userMessage.setContent(userContent);
                    userMessage.setCreatedAt(OffsetDateTime.now());

                    session.setTurnCount(nextTurnCount);
                    session.setStage(activeStage);
                    OffsetDateTime now = OffsetDateTime.now();
                    session.setUpdatedAt(now);
                    if (activeStage == InterviewStage.COMPLETED && session.getCompletedAt() == null) {
                        session.setCompletedAt(now);
                    }

                    return messageRepository
                            .save(userMessage)
                            .then(sessionRepository.save(session));
                })
                .flatMapMany(session -> {
                    Mono<List<AiMessagePayload>> historyMono = messageRepository
                            .findBySessionIdOrderByCreatedAtAsc(sessionId)
                            .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                            .map(msg -> new AiMessagePayload(
                                    msg.getRole().name().toLowerCase(Locale.ROOT),
                                    msg.getContent()))
                            .collectList();

                    Mono<StoredResumeMetadata> resumeMono = resumeService
                            .findResumeMetadata(session.getResumeId());

                    return Mono.zip(historyMono, resumeMono)
                            .flatMapMany(tuple -> {
                                List<AiMessagePayload> history = tuple.getT1();
                                StoredResumeMetadata resumeMeta = tuple.getT2();

                                AiGenerateStreamRequest aiRequest = new AiGenerateStreamRequest(
                                        history,
                                        session.getStage(),
                                        session.getTargetRole(),
                                        resumeMeta.skills(),
                                        resumeMeta.experienceSummary());

                                StringBuilder assistantBuffer = new StringBuilder();

                                Flux<AiStreamChunk> tokenStream = aiServiceClient
                                        .streamChat(aiRequest)
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
                                    return messageRepository
                                            .save(assistantMessage)
                                            .doOnSuccess(ignored -> {
                                                if (session.getStage() == InterviewStage.COMPLETED) {
                                                    triggerAsyncEvaluation(sessionId);
                                                }
                                            })
                                            .then();
                                });

                                return tokenStream
                                        .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                                                .data(chunk)
                                                .build())
                                        .concatWith(persistAssistantMono.then(Mono.empty()));
                            });
                });
    }

    InterviewStage resolveNextStage(InterviewStage currentStage, int turnCount) {
        int introEndTurn = stageProperties.intro();
        int technicalEndTurn = introEndTurn + stageProperties.technical();
        int behavioralEndTurn = technicalEndTurn + stageProperties.behavioral();

        InterviewStage computed;
        if (turnCount > behavioralEndTurn) {
            computed = InterviewStage.COMPLETED;
        } else if (turnCount > technicalEndTurn) {
            computed = InterviewStage.BEHAVIORAL;
        } else if (turnCount > introEndTurn) {
            computed = InterviewStage.TECHNICAL;
        } else {
            computed = InterviewStage.INTRO;
        }

        if (currentStage != null && currentStage.ordinal() > computed.ordinal()) {
            return currentStage;
        }
        return computed;
    }

    void triggerAsyncEvaluation(UUID sessionId) {
        generateAndPersistEvaluation(sessionId)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        report -> log.info(
                                "Saved async evaluation report for session {} (overall={})",
                                sessionId,
                                report.overallScore()),
                        err -> log.error(
                                "Async evaluation failed for session {}: {}",
                                sessionId,
                                err.getMessage()));
    }

    public Mono<EvaluationReport> generateAndPersistEvaluation(UUID sessionId) {
        return sessionRepository
                .findById(sessionId)
                .flatMap(session -> {
                    Mono<List<AiMessagePayload>> historyMono = messageRepository
                            .findBySessionIdOrderByCreatedAtAsc(sessionId)
                            .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                            .map(msg -> new AiMessagePayload(
                                    msg.getRole().name().toLowerCase(Locale.ROOT),
                                    msg.getContent()))
                            .collectList();

                    Mono<StoredResumeMetadata> resumeMono = resumeService
                            .findResumeMetadata(session.getResumeId());

                    return Mono.zip(historyMono, resumeMono)
                            .flatMap(tuple -> {
                                List<AiMessagePayload> history = tuple.getT1();
                                if (history.isEmpty()) {
                                    return Mono.empty();
                                }
                                StoredResumeMetadata resumeMeta = tuple.getT2();
                                AiEvaluateSessionRequest evalRequest = new AiEvaluateSessionRequest(
                                        history,
                                        session.getTargetRole(),
                                        resumeMeta.skills(),
                                        resumeMeta.experienceSummary());

                                return aiServiceClient
                                        .evaluateSession(evalRequest)
                                        .flatMap(report -> {
                                            session.setEvaluationReport(
                                                    Json.of(serializeEvaluationReport(report)));
                                            session.setUpdatedAt(OffsetDateTime.now());
                                            return sessionRepository
                                                    .save(session)
                                                    .thenReturn(report);
                                        });
                            });
                });
    }

    private SessionResponse toSessionResponse(InterviewSession session) {
        EvaluationReport report = deserializeEvaluationReport(session.getEvaluationReport());
        return SessionResponse.fromEntity(session, report);
    }

    private String serializeEvaluationReport(EvaluationReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize EvaluationReport, storing empty JSON", e);
            return "{}";
        }
    }

    private EvaluationReport deserializeEvaluationReport(Json json) {
        if (json == null) {
            return null;
        }
        String raw = json.asString();
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, EvaluationReport.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize EvaluationReport JSON", e);
            return null;
        }
    }
}
