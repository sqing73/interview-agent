package com.interviewagent.orchestrator.repository;

import com.interviewagent.orchestrator.entity.ResumeEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ResumeRepository extends ReactiveCrudRepository<ResumeEntity, UUID> {

    Flux<ResumeEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
