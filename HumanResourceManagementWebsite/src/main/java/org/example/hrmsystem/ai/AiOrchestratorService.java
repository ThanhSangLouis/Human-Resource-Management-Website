package org.example.hrmsystem.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.example.hrmsystem.ai.dto.AiChatRequest;
import org.example.hrmsystem.ai.dto.AiChatResponse;
import org.example.hrmsystem.ai.dto.AiCitationDto;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.security.AppUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AiOrchestratorService {

    private static final Set<AiIntent> MANAGER_INTENTS = Set.of(
            AiIntent.MGR_PENDING_LEAVE,
            AiIntent.MGR_TEAM_ATTENDANCE
    );

    private static final String[] KW_MGR_PENDING = {
            "pending leave", "approval queue", "danh sách đơn chờ", "danh sach don cho",
            "đơn chờ của team", "don cho cua team", "duyệt đơn nhân viên", "duyet don nhan vien",
            "hàng đợi duyệt", "hang doi duyet", "manager pending", "ds đơn nghỉ", "ds don nghi"
    };
    private static final String[] KW_MGR_LATE = {
            "team late", "đi muộn team", "di muon team", "nhân viên muộn", "nhan vien muon",
            "late employees", "chấm công muộn nhóm"
    };
    private static final String[] KW_SUMMARY = {
            "tóm tắt", "tom tat", "summary", "unread", "chưa đọc", "chua doc", "bullet"
    };
    private static final String[] KW_FAQ = {
            "quy định", "quy dinh", "policy", "faq", "luật", "luat", "hướng dẫn", "huong dan",
            "annual rule", "ngày phép năm", "ngay phep nam", "công ty cho", "cong ty cho"
    };
    private static final String[] KW_SELF_LEAVE = {
            "đơn nghỉ", "don nghi", "leave request", "ngày phép", "ngay phep", "phép còn", "phep con",
            "annual leave", "xin nghỉ", "xin nghi"
    };
    private static final String[] KW_SELF_ATT = {
            "chấm công", "cham cong", "check-in", "check in", "checkout", "check-out", "đi muộn hôm nay",
            "di muon hom nay", "attendance", "ca làm", "ca lam", "vào ca", "vao ca"
    };
    private static final String[] KW_SELF_NOTIF = {
            "thông báo", "thong bao", "notification", "tin nhắn hệ thống", "tin nhan he thong"
    };

    private static final String SYSTEM_INSTRUCTION = """
            Bạn là trợ lý HR nội bộ cho ứng dụng HRM. Chỉ trả lời dựa trên JSON facts và đoạn policy được cung cấp.
            Nếu thiếu dữ liệu, nói rõ là hệ thống không có thông tin đó. Trả lời ngắn gọn, lịch sự.
            Nếu user viết tiếng Việt thì trả lời tiếng Việt; nếu tiếng Anh thì trả lời tiếng Anh.
            Không bịa số liệu ngoài facts.""";

    private final AiGuardrailService guardrail;
    private final PolicyKnowledgeService policyKnowledgeService;
    private final HrAiToolService hrAiToolService;
    private final GeminiClient geminiClient;
    private final GeminiClientProxy geminiClientProxy;
    private final AiChatRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;
    private final boolean geminiEnabled;

    public AiOrchestratorService(
            AiGuardrailService guardrail,
            PolicyKnowledgeService policyKnowledgeService,
            HrAiToolService hrAiToolService,
            GeminiClient geminiClient,
            GeminiClientProxy geminiClientProxy,
            AiChatRateLimiter rateLimiter,
            JsonMapper jsonMapper,
            @Value("${ai.gemini.enabled:true}") boolean geminiEnabled
    ) {
        this.guardrail = guardrail;
        this.policyKnowledgeService = policyKnowledgeService;
        this.hrAiToolService = hrAiToolService;
        this.geminiClient = geminiClient;
        this.geminiClientProxy = geminiClientProxy;
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
        this.geminiEnabled = geminiEnabled;
    }

    public AiChatResponse chat(AppUserDetails principal, AiChatRequest request) {
        String message = request.getMessage().trim();
        guardrail.validateInput(message);

        if (!rateLimiter.allow(principal.getUserId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều tin nhắn mỗi phút, thử lại sau.");
        }

        AiIntent intent = classifyIntent(message);
        if (MANAGER_INTENTS.contains(intent) && Role.EMPLOYEE.name().equals(principal.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ quản lý / HR / Admin mới xem được dữ liệu team."
            );
        }

        List<AiCitationDto> citations = new ArrayList<>();
        if (intent == AiIntent.FAQ) {
            citations.addAll(policyKnowledgeService.findRelevant(message, 2));
        }

        Map<String, Object> dataSnapshot = hrAiToolService.runTools(intent, principal, message);
        String policyBlock = policyKnowledgeService.combinedExcerptText(citations);

        boolean hasHistory = request.getHistory() != null && !request.getHistory().isEmpty();
        String userPayload = buildUserPayload(intent, message, dataSnapshot, policyBlock, hasHistory, request);

        final boolean fallback;
        Optional<String> modelText = Optional.empty();
        if (!geminiEnabled || !geminiClient.isConfigured()) {
            fallback = true;
        } else {
            modelText = geminiClientProxy.generate(SYSTEM_INSTRUCTION, userPayload, hasHistory);
            fallback = modelText.isEmpty();
        }

        String reply = modelText
                .map(guardrail::validateOutput)
                .orElseGet(() -> guardrail.validateOutput(buildFallbackReply(intent, dataSnapshot, citations, fallback)));

        AiChatResponse res = new AiChatResponse();
        res.setReply(reply);
        res.setIntent(intent.name());
        res.setDataSnapshot(dataSnapshot);
        res.setFallback(fallback);
        if (!citations.isEmpty()) {
            res.setCitations(citations);
        }
        return res;
    }

    private String buildUserPayload(
            AiIntent intent,
            String userMessage,
            Map<String, Object> dataSnapshot,
            String policyText,
            boolean hasHistory,
            AiChatRequest request
    ) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("intent", intent.name());
            envelope.put("userMessage", userMessage);
            envelope.put("factsJson", dataSnapshot);
            envelope.put("policyExcerpts", policyText);
            if (hasHistory) {
                List<Map<String, String>> hist = new ArrayList<>();
                request.getHistory().forEach(m -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("role", m.getRole());
                    row.put("text", m.getText());
                    hist.add(row);
                });
                envelope.put("priorTurns", hist);
            }
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope);
        } catch (JacksonException e) {
            return "intent=" + intent + "\nuser=" + userMessage + "\nfacts=" + String.valueOf(dataSnapshot);
        }
    }

    private String buildFallbackReply(
            AiIntent intent,
            Map<String, Object> dataSnapshot,
            List<AiCitationDto> citations,
            boolean networkOff
    ) {
        String prefix = networkOff
                ? "[Chế độ không gọi Gemini hoặc API lỗi — bản tóm tắt máy]\n"
                : "[Phản hồi dự phòng]\n";
        StringBuilder sb = new StringBuilder(prefix);
        sb.append("Ý định: ").append(intent.name()).append(".\n");
        if (!citations.isEmpty()) {
            sb.append("Tham chiếu nội bộ: ");
            sb.append(String.join(", ", citations.stream().map(AiCitationDto::getSource).toList()));
            sb.append(".\n");
        }
        sb.append("Dữ liệu (tóm tắt khóa): ");
        sb.append(String.join(", ", dataSnapshot.keySet()));
        sb.append(".\nXem chi tiết trong JSON dataSnapshot trên giao diện API nếu có.");
        return sb.toString();
    }

    private AiIntent classifyIntent(String message) {
        String n = guardrail.normalizeForMatch(message);
        if (containsAny(n, KW_MGR_PENDING)) {
            return AiIntent.MGR_PENDING_LEAVE;
        }
        if (containsAny(n, KW_MGR_LATE)) {
            return AiIntent.MGR_TEAM_ATTENDANCE;
        }
        if (containsAny(n, KW_SUMMARY)) {
            return AiIntent.NOTIF_SUMMARY;
        }
        if (containsAny(n, KW_FAQ)) {
            return AiIntent.FAQ;
        }
        if (containsAny(n, KW_SELF_LEAVE)) {
            return AiIntent.SELF_LEAVE;
        }
        if (containsAny(n, KW_SELF_ATT)) {
            return AiIntent.SELF_ATTENDANCE;
        }
        if (containsAny(n, KW_SELF_NOTIF)) {
            return AiIntent.SELF_NOTIFICATIONS;
        }
        return AiIntent.FAQ;
    }

    private static boolean containsAny(String normalized, String[] keys) {
        for (String k : keys) {
            if (normalized.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
