package org.example.hrmsystem.ai;

import org.example.hrmsystem.ai.dto.AiChatRequest;
import org.example.hrmsystem.ai.dto.AiChatResponse;
import org.example.hrmsystem.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private static final int PREVIEW_MAX = 120;

    private final AiChatAuditRepository repository;

    public AiAuditService(AiChatAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            AppUserDetails principal,
            AiChatRequest request,
            AiChatResponse response,
            long durationMs
    ) {
        try {
            AiChatAudit row = baseRow(principal, request);
            row.setIntent(response.getIntent());
            row.setFallbackFlag(response.isFallback());
            row.setOutcome(AiChatAuditOutcome.SUCCESS);
            row.setHttpStatus(200);
            row.setDurationMs(durationMs);
            repository.save(row);
        } catch (Exception e) {
            log.warn("AI audit (success) skipped: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponseStatus(
            AppUserDetails principal,
            AiChatRequest request,
            ResponseStatusException ex,
            long durationMs
    ) {
        try {
            AiChatAudit row = baseRow(principal, request);
            int status = ex.getStatusCode().value();
            row.setHttpStatus(status);
            row.setDurationMs(durationMs);
            row.setErrorMessage(trim(ex.getReason(), 500));
            row.setFallbackFlag(false);
            if (status == 403) {
                row.setOutcome(AiChatAuditOutcome.FORBIDDEN);
            } else if (status == 429) {
                row.setOutcome(AiChatAuditOutcome.TOO_MANY_REQUESTS);
            } else {
                row.setOutcome(AiChatAuditOutcome.ERROR);
            }
            repository.save(row);
        } catch (Exception e) {
            log.warn("AI audit (rse) skipped: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordValidation(
            AppUserDetails principal,
            AiChatRequest request,
            IllegalArgumentException ex,
            long durationMs
    ) {
        try {
            AiChatAudit row = baseRow(principal, request);
            row.setOutcome(AiChatAuditOutcome.VALIDATION_ERROR);
            row.setHttpStatus(400);
            row.setDurationMs(durationMs);
            row.setErrorMessage(trim(ex.getMessage(), 500));
            repository.save(row);
        } catch (Exception e) {
            log.warn("AI audit (validation) skipped: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(
            AppUserDetails principal,
            AiChatRequest request,
            Exception ex,
            long durationMs
    ) {
        try {
            AiChatAudit row = baseRow(principal, request);
            row.setOutcome(AiChatAuditOutcome.ERROR);
            row.setHttpStatus(500);
            row.setDurationMs(durationMs);
            row.setErrorMessage(trim(ex.getMessage(), 500));
            repository.save(row);
        } catch (Exception e) {
            log.warn("AI audit (error) skipped: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnauthorized(AiChatRequest request, long durationMs) {
        try {
            AiChatAudit row = new AiChatAudit();
            row.setCreatedAt(Instant.now());
            row.setUserId(-1L);
            row.setRoleName("ANONYMOUS");
            fillInput(row, request);
            row.setOutcome(AiChatAuditOutcome.UNAUTHORIZED);
            row.setHttpStatus(401);
            row.setDurationMs(durationMs);
            repository.save(row);
        } catch (Exception e) {
            log.warn("AI audit (401) skipped: {}", e.getMessage());
        }
    }

    private static AiChatAudit baseRow(AppUserDetails principal, AiChatRequest request) {
        AiChatAudit row = new AiChatAudit();
        row.setCreatedAt(Instant.now());
        row.setUserId(principal.getUserId());
        row.setEmployeeId(principal.getEmployeeId());
        row.setRoleName(principal.getRole());
        fillInput(row, request);
        return row;
    }

    private static void fillInput(AiChatAudit row, AiChatRequest request) {
        String msg = request.getMessage() == null ? "" : request.getMessage().trim();
        row.setInputHash(sha256Hex(msg));
        row.setInputPreview(preview(msg));
    }

    private static String preview(String msg) {
        String oneLine = msg.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= PREVIEW_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, PREVIEW_MAX) + "…";
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "0".repeat(64);
        }
    }
}
