package org.example.hrmsystem.ai;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AiGuardrailService {

    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)(<script|javascript:|data:text/html|onerror\\s*=|onload\\s*=)"
    );

    public void validateInput(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Tin nhắn không được để trống");
        }
        if (message.length() > 2000) {
            throw new IllegalArgumentException("Tin nhắn quá dài (tối đa 2000 ký tự)");
        }
        if (SUSPICIOUS.matcher(message).find()) {
            throw new IllegalArgumentException("Nội dung không được phép");
        }
    }

    public String validateOutput(String text) {
        if (text == null || text.isBlank()) {
            return "(Không có nội dung trả lời)";
        }
        if (text.length() > 8000) {
            return text.substring(0, 8000) + "\n…";
        }
        return text;
    }

    public String normalizeForMatch(String message) {
        return message.toLowerCase(Locale.ROOT);
    }
}
