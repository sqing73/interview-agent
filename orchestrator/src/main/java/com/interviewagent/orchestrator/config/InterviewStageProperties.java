package com.interviewagent.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interview.stage-questions")
public record InterviewStageProperties(
        int intro,
        int technical,
        int behavioral) {

    public InterviewStageProperties {
        if (intro <= 0) {
            intro = 1;
        }
        if (technical <= 0) {
            technical = 3;
        }
        if (behavioral <= 0) {
            behavioral = 2;
        }
    }
}
