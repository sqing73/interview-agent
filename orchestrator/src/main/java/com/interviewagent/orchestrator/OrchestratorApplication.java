package com.interviewagent.orchestrator;

import com.interviewagent.orchestrator.config.InterviewStageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableConfigurationProperties(InterviewStageProperties.class)
public class OrchestratorApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
