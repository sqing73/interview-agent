package com.interviewagent.orchestrator.service;

import com.interviewagent.orchestrator.client.AiServiceClient;
import com.interviewagent.orchestrator.config.InterviewStageProperties;
import com.interviewagent.orchestrator.dto.ResumeDtos.StoredResumeMetadata;
import com.interviewagent.orchestrator.dto.SessionDtos.AiGenerateStreamRequest;
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
    private final ResumeService resumeService;
    private final AiServiceClient aiServiceClient;
    private final InterviewStageProperties stageProperties;

    public InterviewSessionService(
            InterviewSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            ResumeService resumeService,
            AiServiceClient aiServiceClient,
            InterviewStageProperties stageProperties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.resumeService = resumeService;
        this.aiServiceClient = aiServiceClient;
        this.stageProperties = stageProperties;
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
                .map(SessionResponse::fromEntity);
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
                    session.setUpdatedAt(OffsetDateTime.now());

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
                                    return messageRepository.save(assistantMessage).then();
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
}
