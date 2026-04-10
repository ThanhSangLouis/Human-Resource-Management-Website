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
        if (text.length() > 14000) {
            return text.substring(0, 14000) + "\n…";
        }
        return text;
    }

    /**
     * Gộp mọi khoảng trắng Unicode (NBSP từ Word/Excel, narrow space, v.v.) thành space ASCII.
     * Tránh đếm sai số từ và không khớp keyword khi người dùng dán văn bản.
     */
    public static String normalizeWhitespace(String message) {
        if (message == null) {
            return "";
        }
        String s = message.strip();
        if (s.isEmpty()) {
            return "";
        }
        return s.replaceAll("[\\p{Zs}\\t]+", " ").trim().replaceAll(" +", " ");
    }

    public String normalizeForMatch(String message) {
        return normalizeWhitespace(message).toLowerCase(Locale.ROOT);
    }
}
