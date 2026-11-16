package com.interviewagent.orchestrator.repository;

import com.interviewagent.orchestrator.entity.ChatMessageEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessageEntity, UUID> {

    Flux<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
