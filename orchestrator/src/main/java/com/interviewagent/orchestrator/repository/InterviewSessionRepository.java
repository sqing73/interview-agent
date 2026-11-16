package com.interviewagent.orchestrator.repository;

import com.interviewagent.orchestrator.entity.InterviewSession;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InterviewSessionRepository extends ReactiveCrudRepository<InterviewSession, UUID> {
}
