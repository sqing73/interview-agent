package com.interviewagent.orchestrator.client;

import com.interviewagent.orchestrator.dto.ResumeDtos.AiParseResumeRequest;
import com.interviewagent.orchestrator.dto.ResumeDtos.AiParseResumeResponse;
import com.interviewagent.orchestrator.dto.SessionDtos.AiEvaluateSessionRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiGenerateStreamRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import com.interviewagent.orchestrator.dto.SessionDtos.EvaluationReport;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AiServiceClient {

    private static final ParameterizedTypeReference<ServerSentEvent<AiStreamChunk>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient aiServiceWebClient;

    public AiServiceClient(WebClient aiServiceWebClient) {
        this.aiServiceWebClient = aiServiceWebClient;
    }

    public Flux<AiStreamChunk> streamChat(AiGenerateStreamRequest request) {
        return aiServiceWebClient
                .post()
                .uri("/api/v1/generate-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .mapNotNull(ServerSentEvent::data);
    }

    public Mono<AiParseResumeResponse> parseResume(String rawText) {
        AiParseResumeRequest request = new AiParseResumeRequest(rawText);
        return aiServiceWebClient
                .post()
                .uri("/api/v1/parse-resume")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiParseResumeResponse.class);
    }

    public Mono<EvaluationReport> evaluateSession(AiEvaluateSessionRequest request) {
        return aiServiceWebClient
                .post()
                .uri("/api/v1/evaluate-session")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EvaluationReport.class);
    }
}
