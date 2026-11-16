package com.interviewagent.orchestrator.client;

import com.interviewagent.orchestrator.dto.SessionDtos.AiGenerateStreamRequest;
import com.interviewagent.orchestrator.dto.SessionDtos.AiMessagePayload;
import com.interviewagent.orchestrator.dto.SessionDtos.AiStreamChunk;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
public class AiServiceClient {

    private static final ParameterizedTypeReference<ServerSentEvent<AiStreamChunk>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient aiServiceWebClient;

    public AiServiceClient(WebClient aiServiceWebClient) {
        this.aiServiceWebClient = aiServiceWebClient;
    }

    public Flux<AiStreamChunk> streamChat(List<AiMessagePayload> messages) {
        AiGenerateStreamRequest request = new AiGenerateStreamRequest(messages);
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
}
