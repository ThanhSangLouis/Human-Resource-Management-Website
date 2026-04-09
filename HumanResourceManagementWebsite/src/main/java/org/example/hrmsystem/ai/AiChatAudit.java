package org.example.hrmsystem.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_chat_audit")
public class AiChatAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "role_name", length = 32)
    private String roleName;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "input_preview", length = 255)
    private String inputPreview;

    @Column(name = "intent", length = 64)
    private String intent;

    @Column(name = "fallback_flag", nullable = false)
    private boolean fallbackFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private AiChatAuditOutcome outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public String getInputPreview() {
        return inputPreview;
    }

    public void setInputPreview(String inputPreview) {
        this.inputPreview = inputPreview;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public boolean isFallbackFlag() {
        return fallbackFlag;
    }

    public void setFallbackFlag(boolean fallbackFlag) {
        this.fallbackFlag = fallbackFlag;
    }

    public AiChatAuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AiChatAuditOutcome outcome) {
        this.outcome = outcome;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
