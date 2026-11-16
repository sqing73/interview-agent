package com.interviewagent.orchestrator.controller;

import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.dto.SessionDtos.CreateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.MessageResponse;
import com.interviewagent.orchestrator.dto.SessionDtos.SendMessageRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.SessionResponse;
import com.interviewagent.orchestrator.service.InterviewSessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/sessions")
public class InterviewSessionController {

    private final InterviewSessionService sessionService;

    public InterviewSessionController(InterviewSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SessionResponse> createSession(
            @RequestBody(required = false) CreateSessionRequest request) {
        return sessionService.createSession(request);
    }

    @GetMapping("/{id}")
    public Mono<SessionResponse> getSession(@PathVariable("id") UUID sessionId) {
        return sessionService.getSession(sessionId);
    }

    @GetMapping("/{id}/messages")
    public Mono<List<MessageResponse>> getMessages(@PathVariable("id") UUID sessionId) {
        return sessionService.getMessages(sessionId);
    }

    @PostMapping(
            value = "/{id}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamChunk>> sendMessage(
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        return sessionService.streamCandidateMessage(sessionId, request.content());
    }
}
