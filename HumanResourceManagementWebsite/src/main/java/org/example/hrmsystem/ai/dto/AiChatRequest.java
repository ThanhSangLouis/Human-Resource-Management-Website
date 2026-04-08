package org.example.hrmsystem.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AiChatRequest {

    @NotBlank
    @Size(max = 2000)
    private String message;

    @Valid
    private List<AiChatMessageDto> history;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<AiChatMessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<AiChatMessageDto> history) {
        this.history = history;
    }
}
