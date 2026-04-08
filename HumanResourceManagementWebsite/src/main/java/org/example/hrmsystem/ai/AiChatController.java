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

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiOrchestratorService orchestratorService;

    public AiChatController(AiOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody AiChatRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(orchestratorService.chat(principal, request));
    }
}
