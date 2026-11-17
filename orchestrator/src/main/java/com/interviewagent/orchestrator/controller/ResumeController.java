package com.interviewagent.orchestrator.controller;

import com.interviewagent.orchestrator.dto.ResumeDtos.ResumeResponse;
import com.interviewagent.orchestrator.service.ResumeService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResumeResponse> uploadResume(
            @RequestPart("file") FilePart filePart,
            @RequestParam(value = "userId", required = false) UUID userId) {
        return resumeService.ingestPdfResume(filePart, userId);
    }

    @GetMapping("/{id}")
    public Mono<ResumeResponse> getResume(@PathVariable UUID id) {
        return resumeService.getResume(id);
    }
}
