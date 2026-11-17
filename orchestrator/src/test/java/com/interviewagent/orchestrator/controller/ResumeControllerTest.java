package com.interviewagent.orchestrator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.interviewagent.orchestrator.dto.ResumeDtos.ResumeResponse;
import com.interviewagent.orchestrator.service.InterviewSessionService;
import com.interviewagent.orchestrator.service.ResumeService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@WebFluxTest(ResumeController.class)
class ResumeControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ResumeService resumeService;

    @Test
    void uploadResumeReturnsCreatedWithParsedSkills() {
        UUID resumeId = UUID.randomUUID();
        ResumeResponse mockResponse = new ResumeResponse(
                resumeId,
                InterviewSessionService.DEFAULT_DEMO_USER_ID,
                "resume.pdf",
                "Shan Qing - Java 21, Spring WebFlux, PostgreSQL",
                List.of("Java 21", "Spring WebFlux", "PostgreSQL"),
                "Backend engineer experienced in reactive microservices.",
                OffsetDateTime.now());

        when(resumeService.ingestPdfResume(any(), isNull()))
                .thenReturn(Mono.just(mockResponse));

        MultipartBodyBuilder multipartBuilder = new MultipartBodyBuilder();
        multipartBuilder
                .part("file", new ByteArrayResource("%PDF-1.4 dummy".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "resume.pdf";
                    }
                })
                .contentType(MediaType.APPLICATION_PDF);

        webTestClient
                .post()
                .uri("/api/resumes")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBuilder.build()))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(resumeId.toString())
                .jsonPath("$.fileName").isEqualTo("resume.pdf")
                .jsonPath("$.parsedSkills[0]").isEqualTo("Java 21")
                .jsonPath("$.parsedSkills[1]").isEqualTo("Spring WebFlux")
                .jsonPath("$.experienceSummary").isEqualTo(
                        "Backend engineer experienced in reactive microservices.");
    }
}
