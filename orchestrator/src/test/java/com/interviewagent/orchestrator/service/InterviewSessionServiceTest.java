package com.interviewagent.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewagent.orchestrator.client.AiServiceClient;
import com.interviewagent.orchestrator.config.InterviewStageProperties;
import com.interviewagent.orchestrator.dto.ResumeDtos.StoredResumeMetadata;
import com.interviewagent.orchestrator.dto.SessionDtos.AiGenerateStreamRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.entity.ChatMessageEntity;
import com.interviewagent.orchestrator.entity.InterviewSession;
import com.interviewagent.orchestrator.entity.InterviewStage;
import com.interviewagent.orchestrator.entity.MessageRole;
import com.interviewagent.orchestrator.repository.ChatMessageRepository;
import com.interviewagent.orchestrator.repository.InterviewSessionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ResumeService resumeService;

    @Mock
    private AiServiceClient aiServiceClient;

    private InterviewSessionService sessionService;

    @BeforeEach
    void setUp() {
        InterviewStageProperties stageProperties = new InterviewStageProperties(1, 3, 2);
        sessionService = new InterviewSessionService(
                sessionRepository,
                messageRepository,
                resumeService,
                aiServiceClient,
                stageProperties);
    }

    @Test
    void resolveNextStageTransitionsByTurnCount() {
        assertThat(sessionService.resolveNextStage(InterviewStage.INTRO, 1))
                .isEqualTo(InterviewStage.INTRO);
        assertThat(sessionService.resolveNextStage(InterviewStage.INTRO, 2))
                .isEqualTo(InterviewStage.TECHNICAL);
        assertThat(sessionService.resolveNextStage(InterviewStage.TECHNICAL, 4))
                .isEqualTo(InterviewStage.TECHNICAL);
        assertThat(sessionService.resolveNextStage(InterviewStage.TECHNICAL, 5))
                .isEqualTo(InterviewStage.BEHAVIORAL);
        assertThat(sessionService.resolveNextStage(InterviewStage.BEHAVIORAL, 7))
                .isEqualTo(InterviewStage.COMPLETED);
    }

    @Test
    void streamCandidateMessageAdvancesStageAndPassesResumeContext() {
        UUID sessionId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();

        InterviewSession existingSession = new InterviewSession();
        existingSession.setId(sessionId);
        existingSession.setResumeId(resumeId);
        existingSession.setTargetRole("Senior Backend Engineer");
        existingSession.setStage(InterviewStage.INTRO);
        existingSession.setTurnCount(1);
        existingSession.setCreatedAt(OffsetDateTime.now());
        existingSession.setUpdatedAt(OffsetDateTime.now());

        ChatMessageEntity savedUserMsg = new ChatMessageEntity();
        savedUserMsg.setSessionId(sessionId);
        savedUserMsg.setRole(MessageRole.USER);
        savedUserMsg.setStage(InterviewStage.TECHNICAL);
        savedUserMsg.setContent("I built reactive pipelines with WebFlux.");

        when(sessionRepository.findById(sessionId)).thenReturn(Mono.just(existingSession));
        when(messageRepository.save(any(ChatMessageEntity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(sessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Flux.just(savedUserMsg));
        when(resumeService.findResumeMetadata(resumeId))
                .thenReturn(Mono.just(new StoredResumeMetadata(
                        List.of("Java 21", "Spring WebFlux"),
                        "Experienced in reactive Java.")));
        when(aiServiceClient.streamChat(any(AiGenerateStreamRequest.class)))
                .thenReturn(Flux.just(
                        new AiStreamChunk("How does ", null, null),
                        new AiStreamChunk("backpressure work?", null, null),
                        new AiStreamChunk(null, true, null)));

        Flux<ServerSentEvent<AiStreamChunk>> stream = sessionService
                .streamCandidateMessage(sessionId, "I built reactive pipelines with WebFlux.");

        StepVerifier.create(stream.mapNotNull(ServerSentEvent::data))
                .expectNext(new AiStreamChunk("How does ", null, null))
                .expectNext(new AiStreamChunk("backpressure work?", null, null))
                .expectNext(new AiStreamChunk(null, true, null))
                .verifyComplete();

        ArgumentCaptor<AiGenerateStreamRequest> requestCaptor =
                ArgumentCaptor.forClass(AiGenerateStreamRequest.class);
        verify(aiServiceClient).streamChat(requestCaptor.capture());

        AiGenerateStreamRequest captured = requestCaptor.getValue();
        assertThat(captured.stage()).isEqualTo(InterviewStage.TECHNICAL);
        assertThat(captured.targetRole()).isEqualTo("Senior Backend Engineer");
        assertThat(captured.resumeSkills()).containsExactly("Java 21", "Spring WebFlux");
        assertThat(captured.resumeSummary()).isEqualTo("Experienced in reactive Java.");
    }
}
