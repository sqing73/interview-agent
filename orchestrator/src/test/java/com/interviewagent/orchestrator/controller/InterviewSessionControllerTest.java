package com.interviewagent.orchestrator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.dto.SessionDtos.CreateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.SendMessageRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.SessionResponse;
import com.interviewagent.orchestrator.entity.InterviewStage;
import com.interviewagent.orchestrator.service.InterviewSessionService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@WebFluxTest(InterviewSessionController.class)
class InterviewSessionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private InterviewSessionService sessionService;

    @Test
    void createSessionReturnsCreatedSession() {
        UUID sessionId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();
        SessionResponse mockResponse = new SessionResponse(
                sessionId,
                InterviewSessionService.DEFAULT_DEMO_USER_ID,
                resumeId,
                "Backend Engineer",
                InterviewStage.INTRO,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now());

        when(sessionService.createSession(any())).thenReturn(Mono.just(mockResponse));

        webTestClient
                .post()
                .uri("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateSessionRequest(null, resumeId, "Backend Engineer"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(sessionId.toString())
                .jsonPath("$.resumeId").isEqualTo(resumeId.toString())
                .jsonPath("$.targetRole").isEqualTo("Backend Engineer")
                .jsonPath("$.stage").isEqualTo("INTRO");
    }

    @Test
    void sendMessageStreamsSseTokens() {
        UUID sessionId = UUID.randomUUID();
        Flux<ServerSentEvent<AiStreamChunk>> mockStream = Flux.just(
                ServerSentEvent.<AiStreamChunk>builder()
                        .data(new AiStreamChunk("Hello", null, null))
                        .build(),
                ServerSentEvent.<AiStreamChunk>builder()
                        .data(new AiStreamChunk(" candidate!", null, null))
                        .build(),
                ServerSentEvent.<AiStreamChunk>builder()
                        .data(new AiStreamChunk(null, true, null))
                        .build());

        when(sessionService.streamCandidateMessage(eq(sessionId), eq("Hi, ready to start")))
                .thenReturn(mockStream);

        Flux<AiStreamChunk> result = webTestClient
                .post()
                .uri("/api/sessions/{id}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new SendMessageRequest("Hi, ready to start"))
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(AiStreamChunk.class)
                .getResponseBody();

        StepVerifier.create(result)
                .expectNext(new AiStreamChunk("Hello", null, null))
                .expectNext(new AiStreamChunk(" candidate!", null, null))
                .expectNext(new AiStreamChunk(null, true, null))
                .verifyComplete();
    }
}
