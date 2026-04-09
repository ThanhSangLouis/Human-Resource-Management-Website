package org.example.hrmsystem.ai;

import jakarta.validation.Valid;
import org.example.hrmsystem.ai.dto.AiChatRequest;
import org.example.hrmsystem.ai.dto.AiChatResponse;
import org.example.hrmsystem.security.AppUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiOrchestratorService orchestratorService;
    private final AiAuditService auditService;

    public AiChatController(AiOrchestratorService orchestratorService, AiAuditService auditService) {
        this.orchestratorService = orchestratorService;
        this.auditService = auditService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody AiChatRequest request
    ) {
        long t0 = System.nanoTime();
        if (principal == null) {
            auditService.recordUnauthorized(request, durationMs(t0));
            return ResponseEntity.status(401).build();
        }
        try {
            AiChatResponse response = orchestratorService.chat(principal, request);
            auditService.recordSuccess(principal, request, response, durationMs(t0));
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            auditService.recordResponseStatus(principal, request, ex, durationMs(t0));
            throw ex;
        } catch (IllegalArgumentException ex) {
            auditService.recordValidation(principal, request, ex, durationMs(t0));
            throw ex;
        } catch (RuntimeException ex) {
            auditService.recordError(principal, request, ex, durationMs(t0));
            throw ex;
        }
    }

    private static long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
