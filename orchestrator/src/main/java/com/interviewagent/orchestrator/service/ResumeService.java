package com.interviewagent.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewagent.orchestrator.client.AiServiceClient;
import com.interviewagent.orchestrator.dto.ResumeDtos.ResumeResponse;
import com.interviewagent.orchestrator.dto.ResumeDtos.StoredResumeMetadata;
import com.interviewagent.orchestrator.entity.ResumeEntity;
import com.interviewagent.orchestrator.repository.ResumeRepository;
import io.r2dbc.postgresql.codec.Json;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    public ResumeService(
            ResumeRepository resumeRepository,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper) {
        this.resumeRepository = resumeRepository;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    public Mono<ResumeResponse> ingestPdfResume(FilePart filePart, UUID userId) {
        UUID resolvedUserId = userId != null
                ? userId
                : InterviewSessionService.DEFAULT_DEMO_USER_ID;
        String fileName = filePart.filename() != null && !filePart.filename().isBlank()
                ? filePart.filename()
                : "resume.pdf";

        return readFileBytes(filePart)
                .flatMap(this::extractTextFromPdf)
                .flatMap(extractedText -> aiServiceClient
                        .parseResume(extractedText)
                        .flatMap(parsed -> saveResume(
                                resolvedUserId,
                                fileName,
                                extractedText,
                                parsed.skills(),
                                parsed.experienceSummary())));
    }

    public Mono<ResumeResponse> getResume(UUID resumeId) {
        return resumeRepository
                .findById(resumeId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Resume not found: " + resumeId)))
                .map(this::toResponse);
    }

    private Mono<byte[]> readFileBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Uploaded PDF file is empty")));
    }

    private Mono<String> extractTextFromPdf(byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String extracted = stripper.getText(document);
                if (extracted == null || extracted.isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Uploaded PDF contains no extractable text");
                }
                return extracted.trim();
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Failed to parse PDF file: " + ex.getMessage(),
                        ex);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ResumeResponse> saveResume(
            UUID userId,
            String fileName,
            String extractedText,
            List<String> skills,
            String experienceSummary) {
        List<String> safeSkills = skills != null ? skills : Collections.emptyList();
        String safeSummary = experienceSummary != null ? experienceSummary : "";
        StoredResumeMetadata storedPayload = new StoredResumeMetadata(safeSkills, safeSummary);

        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(storedPayload);
        } catch (JsonProcessingException ex) {
            return Mono.error(new IllegalStateException(
                    "Failed to serialize parsed resume metadata", ex));
        }

        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setFileName(fileName);
        entity.setExtractedText(extractedText);
        entity.setParsedMetadata(Json.of(jsonString));
        entity.setCreatedAt(OffsetDateTime.now());

        return resumeRepository.save(entity).map(this::toResponse);
    }

    public ResumeResponse toResponse(ResumeEntity entity) {
        StoredResumeMetadata stored = parseStoredJson(entity.getParsedMetadata());
        return new ResumeResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getFileName(),
                entity.getExtractedText(),
                stored.skills(),
                stored.experienceSummary(),
                entity.getCreatedAt());
    }

    private StoredResumeMetadata parseStoredJson(Json json) {
        if (json == null) {
            return new StoredResumeMetadata(Collections.emptyList(), "");
        }
        try {
            return objectMapper.readValue(json.asString(), StoredResumeMetadata.class);
        } catch (JsonProcessingException ex) {
            return new StoredResumeMetadata(Collections.emptyList(), "");
        }
    }
}
