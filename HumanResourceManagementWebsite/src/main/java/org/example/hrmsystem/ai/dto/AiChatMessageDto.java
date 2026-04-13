package org.example.hrmsystem.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiChatMessageDto {

    @NotBlank
    @Size(max = 32)
    private String role;

    @NotBlank
    @Size(max = 12_000)   // model reply có thể dài: bảng Markdown, danh sách nhiều bản ghi
    private String text;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
