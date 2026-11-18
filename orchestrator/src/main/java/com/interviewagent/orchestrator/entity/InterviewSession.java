package com.interviewagent.orchestrator.entity;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("interview_sessions")
public class InterviewSession {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("resume_id")
    private UUID resumeId;

    @Column("target_role")
    private String targetRole = "Software Engineer";

    @Column("stage")
    private InterviewStage stage = InterviewStage.INTRO;

    @Column("turn_count")
    private int turnCount = 0;

    @Column("evaluation_report")
    private Json evaluationReport;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public void setResumeId(UUID resumeId) {
        this.resumeId = resumeId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public InterviewStage getStage() {
        return stage;
    }

    public void setStage(InterviewStage stage) {
        this.stage = stage;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Json getEvaluationReport() {
        return evaluationReport;
    }

    public void setEvaluationReport(Json evaluationReport) {
        this.evaluationReport = evaluationReport;
    }
}
