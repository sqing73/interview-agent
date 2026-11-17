package com.interviewagent.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record AiParseResumeRequest(
            @JsonProperty("raw_text") String rawText) {
    }

    public record AiParseResumeResponse(
            List<String> skills,
            @JsonProperty("experience_summary") String experienceSummary) {
    }

    public record StoredResumeMetadata(
            List<String> skills,
            String experienceSummary) {
    }

    public record ResumeResponse(
            UUID id,
            UUID userId,
            String fileName,
            String extractedText,
            List<String> parsedSkills,
            String experienceSummary,
            OffsetDateTime createdAt) {
    }
}
