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

    /** Nội dung tài liệu đính kèm (đã extract qua /api/ai/document/parse). */
    @Size(max = 15_000)
    private String documentContext;

    /** Tên file gốc (hiển thị trong system instruction). */
    @Size(max = 256)
    private String documentName;

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

    public String getDocumentContext() {
        return documentContext;
    }

    public void setDocumentContext(String documentContext) {
        this.documentContext = documentContext;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
}
