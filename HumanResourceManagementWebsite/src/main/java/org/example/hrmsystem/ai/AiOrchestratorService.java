package org.example.hrmsystem.ai;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AiOrchestratorService {

    /**
     * Persona cơ bản — luôn có mặt trong mọi lượt gọi Gemini.
     * Context dữ liệu (facts, policy, scope) được inject dynamically qua
     * {@link #buildDynamicSystemInstruction} rồi gửi làm systemInstruction.
     */
    private static final String SYSTEM_INSTRUCTION_BASE = """
                Bạn là trợ lý HR thông minh, thân thiện và chuyên nghiệp của hệ thống HRM.
                Bạn có 2 vai trò đồng thời:
                1. Trợ lý AI: giải thích tự nhiên, dễ hiểu, giống như đang nói chuyện với người dùng
                2. Trình đọc dữ liệu: cung cấp thông tin chính xác từ hệ thống (DATA CONTEXT)

                ========================
                NGUYÊN TẮC TRẢ LỜI
                ========================

                Luôn trả lời theo 2 bước:

                BƯỚC 1 — GIẢI THÍCH TỰ NHIÊN (BẮT BUỘC)
                - Diễn giải như đang nói chuyện (dùng "bạn", "mình")
                - Không copy nguyên văn dữ liệu hoặc policy
                - Ưu tiên giúp người dùng HIỂU vấn đề
                - Có thể dùng từ thân thiện như: "nha", "ok", "mình giải thích nhé"

                BƯỚC 2 — DỮ LIỆU / QUY ĐỊNH (NẾU CÓ)
                - Nếu có dữ liệu → hiển thị rõ ràng, chính xác
                - Nếu là nhiều bản ghi → dùng bảng Markdown
                - Nếu là policy → tóm tắt ngắn gọn, KHÔNG copy dài dòng

                Format gợi ý:

                [Giải thích tự nhiên]

                Dữ liệu / Quy định:
                - hoặc bảng
                - hoặc bullet ngắn gọn

                ========================
                PHONG CÁCH
                ========================

                - Trả lời tự nhiên, có cảm xúc như chuyên gia HR
                - Không được đọc JSON, không dump raw data
                - Không trả lời kiểu liệt kê khô khan nếu không cần
                - Ưu tiên ngắn gọn, dễ hiểu
                - Dùng Markdown để trình bày rõ ràng
                - Trả lời đúng ngôn ngữ user (VI/EN)

                ========================
                GIỚI HẠN QUAN TRỌNG
                ========================

                - CHỈ sử dụng dữ liệu trong DATA CONTEXT
                - KHÔNG bịa thêm thông tin, số liệu, ngày tháng
                - KHÔNG suy đoán ngoài dữ liệu

                - Nếu KHÔNG có dữ liệu:
                → Trả lời tự nhiên:
                "Hiện tại mình chưa thấy dữ liệu này trong hệ thống..."
                → Gợi ý user kiểm tra hệ thống hoặc liên hệ HR

                - KHÔNG tiết lộ dữ liệu của người khác nếu không được phép

                ========================
                ƯU TIÊN XỬ LÝ
                ========================

                1. Hiểu ý định user (hỏi quy định hay hỏi dữ liệu cá nhân)
                2. Nếu là câu hỏi "hiểu" → tập trung giải thích
                3. Nếu là câu hỏi "số liệu" → trả dữ liệu rõ ràng
                4. Nếu là cả 2 → giải thích trước, dữ liệu sau

                ========================
                NGHIÊM CẤM
                ========================

                - Không copy nguyên văn DATA CONTEXT
                - Không trả lời kiểu tài liệu dài dòng
                - Không nói như robot
            """;

    private final AiGuardrailService guardrail;
    private final AiIntentClassifier intentClassifier;
    private final PolicyKnowledgeService policyKnowledgeService;
    private final HrAiToolService hrAiToolService;
    private final AiConversationContextService conversationContextService;
    private final GeminiClient geminiClient;
    private final GeminiClientProxy geminiClientProxy;
    private final AiChatRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;
    private final boolean geminiEnabled;

    public AiOrchestratorService(
            AiGuardrailService guardrail,
            AiIntentClassifier intentClassifier,
            PolicyKnowledgeService policyKnowledgeService,
            HrAiToolService hrAiToolService,
            AiConversationContextService conversationContextService,
            GeminiClient geminiClient,
            GeminiClientProxy geminiClientProxy,
            AiChatRateLimiter rateLimiter,
            JsonMapper jsonMapper,
            @Value("${ai.gemini.enabled:true}") boolean geminiEnabled) {
        this.guardrail = guardrail;
        this.intentClassifier = intentClassifier;
        this.policyKnowledgeService = policyKnowledgeService;
        this.hrAiToolService = hrAiToolService;
        this.conversationContextService = conversationContextService;
        this.geminiClient = geminiClient;
        this.geminiClientProxy = geminiClientProxy;
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
        this.geminiEnabled = geminiEnabled;
    }

    public AiChatResponse chat(AppUserDetails principal, AiChatRequest request) {
        String message = AiGuardrailService.normalizeWhitespace(request.getMessage().trim());
        guardrail.validateInput(message);

        if (!rateLimiter.allow(principal.getUserId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Quá nhiều tin nhắn mỗi phút, thử lại sau.");
        }

        AiIntent intent = intentClassifier.classify(message);
        if (intentClassifier.managerIntents().contains(intent) && Role.EMPLOYEE.name().equals(principal.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ quản lý / HR / Admin mới xem được dữ liệu team.");
        }

        List<AiCitationDto> citations = new ArrayList<>();
        // CHITCHAT không cần policy — tránh dump tài liệu vào response
        if (intent == AiIntent.FAQ) {
            citations.addAll(policyKnowledgeService.findRelevantWithFallback(message, 4));
        }

        Map<String, Object> dataSnapshot;
        if (intent == AiIntent.HR_EMPLOYEE_DETAILS) {
            dataSnapshot = conversationContextService.getLastEmployeeLookup(principal.getUserId())
                    .map(ctx -> hrAiToolService.employeeDetails(principal, ctx.employeeId()))
                    .orElseGet(() -> Map.of(
                            "error",
                            "Mình chưa biết bạn đang muốn xem chi tiết của nhân viên nào. Bạn hãy tra cứu nhân viên trước (ví dụ: \"cho tôi thông tin nhân viên A\"), rồi hỏi \"chi tiết hơn\"."));
        } else {
            dataSnapshot = hrAiToolService.runTools(intent, principal, message);
            if (intent == AiIntent.HR_EMPLOYEE_LOOKUP) {
                rememberEmployeeLookupContext(principal.getUserId(), dataSnapshot);
            }
        }
        String policyBlock = policyKnowledgeService.combinedExcerptText(citations);

        boolean hasHistory = request.getHistory() != null && !request.getHistory().isEmpty();

        final boolean fallback;
        Optional<String> modelText = Optional.empty();
        if (!geminiEnabled || !geminiClient.isConfigured()) {
            fallback = true;
        } else {
            // Context dữ liệu (facts + policy + role + document) → inject vào system instruction
            String dynamicSysInstruction = buildDynamicSystemInstruction(
                    principal, intent, dataSnapshot, policyBlock,
                    request.getDocumentContext(), request.getDocumentName());

            // History là các turn trước (câu hỏi thực và câu trả lời thực)
            List<Map<String, String>> historyMaps = new ArrayList<>();
            if (hasHistory) {
                request.getHistory().forEach(m -> {
                    Map<String, String> row = new java.util.LinkedHashMap<>();
                    row.put("role", m.getRole());
                    row.put("text", m.getText());
                    historyMaps.add(row);
                });
            }

            // Gửi câu hỏi thực của user như một người đọi hỏi thật — không phải JSON blob
            modelText = geminiClientProxy.generate(dynamicSysInstruction, message, hasHistory, historyMaps);
            fallback = modelText.isEmpty();
        }

        String reply = modelText
                .map(guardrail::validateOutput)
                .orElseGet(
                        () -> guardrail.validateOutput(buildFallbackReply(intent, dataSnapshot, citations, fallback)));

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

    @SuppressWarnings("unchecked")
    private void rememberEmployeeLookupContext(Long userId, Map<String, Object> snap) {
        if (userId == null || snap == null) {
            return;
        }
        Object employeesObj = snap.get("employees");
        if (!(employeesObj instanceof List<?> list) || list.size() != 1) {
            return;
        }
        Object one = list.get(0);
        if (!(one instanceof Map<?, ?> m)) {
            return;
        }
        Object idObj = m.get("id");
        Long empId = null;
        if (idObj instanceof Number n) {
            empId = n.longValue();
        } else if (idObj instanceof String s) {
            try {
                empId = Long.parseLong(s.trim());
            } catch (Exception ignored) {
                empId = null;
            }
        }
        if (empId == null) {
            return;
        }
        Object nameObj = m.get("họ Tên");
        String name = (nameObj == null) ? "" : String.valueOf(nameObj);
        if ("null".equalsIgnoreCase(name)) name = "";
        conversationContextService.rememberLastEmployeeLookup(userId, empId, name);
    }

    /**
     * Xây dựng system instruction động cho mỗi turn:
     * base persona + thông tin role/scope + data context (facts DB) + chính sách
     * liên quan.
     * Gemini sẽ được cung cấp đầy đủ ngữ cảnh nhưng vẫn trả lời câu hỏi thực tự
     * nhiên.
     */
    private String buildDynamicSystemInstruction(
            AppUserDetails principal,
            AiIntent intent,
            Map<String, Object> dataSnapshot,
            String policyBlock,
            String documentContext,
            String documentName) {
        StringBuilder sb = new StringBuilder(SYSTEM_INSTRUCTION_BASE);

        // Thông tin user
        sb.append("\n\n=== THÔNG TIN NGƯỜI DÙNG ===\n");
        sb.append("Role: ").append(principal.getRole()).append("\n");
        sb.append("Phạm vi dữ liệu: ").append(describeDataAccessScope(principal.getRole(), intent)).append("\n");

        // Tài liệu đính kèm — ưu tiên cao nhất nếu có
        if (documentContext != null && !documentContext.isBlank()) {
            String docLabel = (documentName != null && !documentName.isBlank()) ? documentName : "Tài liệu đính kèm";
            sb.append("\n=== TÀI LIỆU ĐÍNH KÈM: ").append(docLabel).append(" ===\n");
            sb.append(documentContext).append("\n");
            sb.append("""
                    Hãy đọc kỹ nội dung tài liệu trên và trả lời câu hỏi của user dựa trên tài liệu đó là chính.
                    Nếu câu hỏi không liên quan đến tài liệu, vẫn trả lời tự nhiên như trợ lý HR.
                    """);
        }

        // Data context (dữ liệu thực từ DB)
        boolean hasFacts = dataSnapshot != null && !dataSnapshot.isEmpty()
                && !Boolean.TRUE.equals(dataSnapshot.get("policyOnly"));
        if (hasFacts) {
            sb.append("\n=== DATA CONTEXT (dữ liệu thực từ hệ thống) ===\n");
            try {
                sb.append(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dataSnapshot));
            } catch (Exception e) {
                sb.append(dataSnapshot.toString());
            }
            sb.append("\n");
            sb.append("Hãy dùng dữ liệu trên để trả lời câu hỏi của user một cách tự nhiên, không đọc nguyên JSON ra.\n");
        }

        // Chính sách nội bộ
        if (policyBlock != null && !policyBlock.isBlank()) {
            sb.append("\n=== CHÍNH SÁCH NỘI BỘ ===\n");
            sb.append(policyBlock).append("\n");
        }

        // Gợi ý format danh sách
        if (intent == AiIntent.MGR_PENDING_LEAVE || intent == AiIntent.MGR_TEAM_ATTENDANCE) {
            sb.append("\nNếu có nhiều bản ghi trong data, hãy trình bày bằng bảng Markdown.\n");
        }

        return sb.toString();
    }

    /** Overload không có document (dành cho code path không gửi file) */
    private String buildDynamicSystemInstruction(
            AppUserDetails principal,
            AiIntent intent,
            Map<String, Object> dataSnapshot,
            String policyBlock) {
        return buildDynamicSystemInstruction(principal, intent, dataSnapshot, policyBlock, null, null);
    }

    private String buildUserPayload(
            AppUserDetails principal,
            AiIntent intent,
            String userMessage,
            Map<String, Object> dataSnapshot,
            String policyText,
            boolean hasHistory,
            AiChatRequest request) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("intent", intent.name());
            envelope.put("userRole", principal.getRole());
            envelope.put("dataAccessScope", describeDataAccessScope(principal.getRole(), intent));
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
        } catch (Exception e) {
            return "intent=" + intent + "\nuser=" + userMessage + "\nfacts=" + String.valueOf(dataSnapshot);
        }
    }

    private static String describeDataAccessScope(String roleName, AiIntent intent) {
        Role r;
        try {
            r = Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return "Không xác định role; chỉ dùng facts trong payload.";
        }
        boolean mgrIntent = intent == AiIntent.MGR_PENDING_LEAVE || intent == AiIntent.MGR_TEAM_ATTENDANCE;
        boolean lookupIntent = intent == AiIntent.HR_EMPLOYEE_LOOKUP;
        boolean lookupDetailIntent = intent == AiIntent.HR_EMPLOYEE_DETAILS;
        final String hrAdmin = "Có thể xem hàng đợi rộng / lọc theo facts; vẫn chỉ dựa trên factsJson, không bịa thêm nhân viên hay số liệu.";
        return switch (r) {
            case EMPLOYEE -> mgrIntent || lookupIntent || lookupDetailIntent
                    ? "Không hợp lệ cho EMPLOYEE (không có facts team)."
                    : (intent == AiIntent.FAQ
                            ? "Chỉ policy chung + facts cá nhân nếu có trong payload; không suy diễn dữ liệu người khác."
                            : "factsJson chỉ phạm vi cá nhân (chấm công/phép/thông báo của user đăng nhập).");
            case MANAGER -> (lookupIntent || lookupDetailIntent)
                    ? "factsJson chứa kết quả tìm kiếm nhân viên trong phạm vi team được quản lý; không xem nhân viên ngoài team."
                    : (mgrIntent
                            ? "factsJson có thể gồm đơn chờ duyệt / đi muộn trong phạm vi team được quản lý; không toàn công ty trừ khi facts nói vậy."
                            : "Tự phục vụ: dữ liệu cá nhân của manager; FAQ: policy chung.");
            case HR -> hrAdmin;
            case ADMIN -> hrAdmin;
        };
    }

    private String buildFallbackReply(
            AiIntent intent,
            Map<String, Object> dataSnapshot,
            List<AiCitationDto> citations,
            boolean networkOff) {
        String body = switch (intent) {
            case CHITCHAT -> "Xin chào! Mình là trợ lý HR của hệ thống HRM. Mình có thể giúp bạn xem chấm công, kiểm tra phép năm, xem đơn chờ duyệt, tra cứu quy định nội bộ và hơn thế nữa. Bạn muốn hỏi về gì?";
            case FAQ -> formatFaqFallback(citations);
            case SELF_ATTENDANCE -> formatSelfAttendanceFallback(dataSnapshot);
            case SELF_LEAVE -> formatSelfLeaveFallback(dataSnapshot);
            case SELF_NOTIFICATIONS, NOTIF_SUMMARY -> formatSelfNotificationsFallback(dataSnapshot);
            case MGR_PENDING_LEAVE -> formatMgrPendingFallback(dataSnapshot);
            case MGR_TEAM_ATTENDANCE -> formatMgrTeamAttendanceFallback(dataSnapshot);
            case DASHBOARD_STATS -> formatDashboardStatsFallback(dataSnapshot);
            case HR_EMPLOYEE_LOOKUP -> formatEmployeeLookupFallback(dataSnapshot);
            case HR_EMPLOYEE_DETAILS -> formatEmployeeDetailsFallback(dataSnapshot);
        };
        if (networkOff) {
            return body + "\n\n" + fallbackFooterNote();
        }
        return body;
    }

    private static String fallbackFooterNote() {
        return "Thông tin được lấy trực tiếp từ dữ liệu trong hệ thống. Bạn có thể hỏi thêm nếu cần chi tiết.";
    }

    private String formatFaqFallback(List<AiCitationDto> citations) {
        if (citations.isEmpty()) {
            return """
                    Hiện chưa tìm thấy đoạn tài liệu nội bộ khớp với câu hỏi.
                    Bạn thử diễn đạt lại (ví dụ: loại phép, thời hạn nộp đơn, quy định đi muộn…), hoặc liên hệ bộ phận HR để được hướng dẫn cụ thể.""";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Dưới đây là nội dung trích từ tài liệu nội bộ, đã chỉnh lại định dạng cho dễ đọc.\n\n");
        for (AiCitationDto c : citations) {
            String displayTitle = friendlyDocTitle(c.getSource());
            sb.append("**").append(displayTitle).append("**\n\n");
            if (c.getExcerpt() != null && !c.getExcerpt().isBlank()) {
                String body = stripLeadingMarkdownHeadingIfSameAs(c.getExcerpt(), displayTitle);
                sb.append(polishPolicyExcerptForFallback(body)).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Tiêu đề hiển thị theo tên file, tiếng Việt — tránh chữ attendance/leave khô
     * khan.
     */
    private static String friendlyDocTitle(String source) {
        String base = humanSourceTitle(source);
        String low = base.toLowerCase(Locale.ROOT);
        if (low.contains("attendance")) {
            return "Quy định chấm công";
        }
        if (low.contains("leave")) {
            return "Chính sách nghỉ phép";
        }
        if (low.contains("general")) {
            return "Nguyên tắc chung";
        }
        return base;
    }

    private static String humanSourceTitle(String source) {
        if (source == null || source.isBlank()) {
            return "Tài liệu";
        }
        String s = source.replace('_', ' ');
        if (s.endsWith(".md")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    /**
     * Tránh lặp tiêu đề: excerpt đã có # ... trùng với friendlyDocTitle thì bỏ dòng
     * đó.
     */
    private static String stripLeadingMarkdownHeadingIfSameAs(String raw, String displayTitle) {
        if (raw == null || raw.isBlank() || displayTitle == null || displayTitle.isBlank()) {
            return raw == null ? "" : raw;
        }
        String keyNorm = normalizePolicyHeadingForCompare(displayTitle);
        String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
        int i = 0;
        while (i < lines.length && lines[i].trim().isEmpty()) {
            i++;
        }
        if (i >= lines.length) {
            return raw;
        }
        String first = lines[i].trim();
        if (!first.startsWith("#")) {
            return raw;
        }
        String headingText = first.replaceFirst("^#{1,6}\\s*", "").replace("*", "").trim();
        String headNorm = normalizePolicyHeadingForCompare(headingText);
        if (headNorm.equals(keyNorm) || headNorm.startsWith(keyNorm) || keyNorm.startsWith(headNorm)) {
            StringBuilder rest = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
                if (rest.length() > 0) {
                    rest.append('\n');
                }
                rest.append(lines[j]);
            }
            return rest.toString().trim();
        }
        return raw;
    }

    private static String normalizePolicyHeadingForCompare(String s) {
        if (s == null) {
            return "";
        }
        String t = s.toLowerCase(Locale.ROOT).replace('*', ' ').trim();
        int p = t.indexOf('(');
        if (p > 0) {
            t = t.substring(0, p).trim();
        }
        return t.replaceAll("\\s+", " ");
    }

    /**
     * Bỏ #/## trong excerpt, chuyển thành dòng in đậm; giữ gạch đầu dòng — dễ
     * render HTML phía client.
     */
    private static String polishPolicyExcerptForFallback(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] lines = raw.replace("\r\n", "\n").split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                String title = t.replaceFirst("^#{1,6}\\s*", "").replace("*", "").trim();
                if (!title.isEmpty()) {
                    out.append("**").append(title).append("**\n");
                }
                continue;
            }
            if (t.startsWith("- ") || t.startsWith("* ")) {
                String rest = t.substring(2).trim();
                out.append("- ").append(rest).append("\n");
                continue;
            }
            out.append(t).append("\n");
        }
        String s = out.toString().trim();
        s = s.replaceAll("\n{3,}", "\n\n");
        return s;
    }

    private String formatSelfAttendanceFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) {
            return String.valueOf(err);
        }
        Map<String, Object> today = toStringKeyMap(snap.get("today"));
        StringBuilder sb = new StringBuilder();
        sb.append("**Chấm công hôm nay**\n\n");
        String dateStr = strVal(today.get("attendanceDate"));
        if (!dateStr.isEmpty()) {
            sb.append("Ngày: ").append(dateStr).append("\n");
        }
        String status = attendanceStatusVi(strVal(today.get("status")));
        if (!status.isEmpty()) {
            sb.append("Trạng thái: ").append(status).append("\n");
        }
        sb.append("Giờ vào: ").append(formatDateTimeField(today.get("checkIn"))).append("\n");
        sb.append("Giờ ra: ").append(formatDateTimeField(today.get("checkOut"))).append("\n");
        sb.append("Giờ công: ").append(formatDecimalField(today.get("workHours"))).append("\n");
        String note = strVal(today.get("note"));
        if (!note.isEmpty()) {
            sb.append("Ghi chú: ").append(note).append("\n");
        }
        String message = translateAttendanceMessage(strVal(today.get("message")));
        if (!message.isEmpty()) {
            sb.append("\n").append(message);
        }
        sb.append("\n\n");
        appendAttendanceHistorySection(sb, snap.get("recentHistory"));
        return sb.toString().trim();
    }

    private void appendAttendanceHistorySection(StringBuilder sb, Object historyObj) {
        Map<String, Object> hist = toStringKeyMap(historyObj);
        String month = strVal(hist.get("month"));
        sb.append("**Một số bản ghi gần đây");
        if (!month.isEmpty()) {
            sb.append(" (tháng ").append(month).append(")");
        }
        sb.append("**\n\n");
        Object contentObj = hist.get("content");
        if (!(contentObj instanceof List<?> rows) || rows.isEmpty()) {
            sb.append("Chưa có dòng chấm công trong khoảng thời gian này.");
            return;
        }
        int n = Math.min(rows.size(), 5);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = toStringKeyMap(rows.get(i));
            String d = strVal(row.get("attendanceDate"));
            String in = formatDateTimeField(row.get("checkIn"));
            String out = formatDateTimeField(row.get("checkOut"));
            String st = attendanceStatusVi(strVal(row.get("status")));
            sb.append("- ").append(d.isEmpty() ? "—" : d)
                    .append(": vào ").append(in.isEmpty() ? "—" : in)
                    .append(", ra ").append(out.isEmpty() ? "—" : out);
            if (!st.isEmpty()) {
                sb.append(" (").append(st).append(")");
            }
            sb.append("\n");
        }
        Object total = hist.get("totalElements");
        if (total instanceof Number num && num.longValue() > n) {
            sb.append("\n(Còn ").append(num.longValue() - n)
                    .append(" bản ghi khác trong tháng — xem đầy đủ tại mục chấm công.)");
        }
    }

    private String formatSelfLeaveFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) {
            return String.valueOf(err);
        }
        StringBuilder sb = new StringBuilder();
        Object quota = snap.get("annualLeaveQuotaDefault");
        Object used = snap.get("annualLeaveApprovedDaysThisYear");
        Object rem = snap.get("annualLeaveRemainingEstimate");
        sb.append("**Phép năm (ước tính trong năm hiện tại)**\n\n");
        sb.append("Hạn mức tham khảo: ").append(strVal(quota)).append(" ngày\n");
        sb.append("Đã duyệt (phép năm): ").append(strVal(used)).append(" ngày\n");
        sb.append("Còn lại (ước tính): ").append(strVal(rem)).append(" ngày\n");
        sb.append("\n(Số liệu dựa trên cấu hình hệ thống và các đơn phép năm đã duyệt.)\n\n");

        Map<String, Object> page = toStringKeyMap(snap.get("myRequestsPage"));
        Object contentObj = page.get("content");
        sb.append("**Đơn nghỉ gần đây**\n\n");
        if (!(contentObj instanceof List<?> list) || list.isEmpty()) {
            sb.append("Chưa có đơn nghỉ nào.");
            return sb.toString().trim();
        }
        int max = Math.min(list.size(), 8);
        for (int i = 0; i < max; i++) {
            Map<String, Object> lr = toStringKeyMap(list.get(i));
            String type = leaveTypeVi(strVal(lr.get("leaveType")));
            String from = strVal(lr.get("startDate"));
            String to = strVal(lr.get("endDate"));
            String st = leaveStatusVi(strVal(lr.get("status")));
            sb.append("- ").append(type).append(": ")
                    .append(from).append(" → ").append(to)
                    .append(", ").append(st).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatSelfNotificationsFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) {
            return String.valueOf(err);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Thông báo**\n\n");
        Object unread = snap.get("unreadCount");
        if (unread != null) {
            sb.append("Chưa đọc: ").append(strVal(unread)).append(" tin\n\n");
        }
        Object recentObj = snap.get("recent");
        if (!(recentObj instanceof List<?> recent) || recent.isEmpty()) {
            sb.append("Chưa có thông báo gần đây.");
            return sb.toString().trim();
        }
        int max = Math.min(recent.size(), 10);
        for (int i = 0; i < max; i++) {
            Map<String, Object> n = toStringKeyMap(recent.get(i));
            String title = strVal(n.get("title"));
            if (title.isEmpty()) {
                title = "Thông báo";
            }
            String created = strVal(n.get("createdAt"));
            if (created.isEmpty()) {
                created = strVal(n.get("sentAt"));
            }
            Object readFlag = n.get("read");
            boolean isRead = Boolean.TRUE.equals(readFlag)
                    || (readFlag instanceof String rs && Boolean.parseBoolean(rs));
            sb.append("- ").append(title);
            if (!created.isEmpty()) {
                sb.append(" (").append(created).append(")");
            }
            sb.append(" — ").append(isRead ? "đã đọc" : "chưa đọc");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String formatMgrPendingFallback(Map<String, Object> snap) {
        Object contentObj = snap.get("content");
        StringBuilder sb = new StringBuilder();
        sb.append("Dưới đây là danh sách các đơn xin nghỉ phép đang chờ duyệt trong phạm vi bạn xem được.\n\n");
        if (!(contentObj instanceof List<?> list) || list.isEmpty()) {
            sb.append("Hiện không có đơn nào chờ duyệt.");
            return sb.toString().trim();
        }
        int max = Math.min(list.size(), 15);
        sb.append(
                "| Mã đơn | Tên nhân viên | Loại phép | Từ ngày | Đến ngày | Số ngày | Trạng thái | Ngày tạo đơn |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (int i = 0; i < max; i++) {
            Map<String, Object> lr = snapshotRowToMap(list.get(i));
            String id = mdTableCell(strVal(lr.get("id")));
            String name = mdTableCell(strVal(lr.get("employeeName")));
            if (name.isEmpty()) {
                name = mdTableCell("—");
            }
            String typeRaw = strVal(lr.get("leaveType"));
            String type = mdTableCell(leaveTypeVi(typeRaw) + (typeRaw.isEmpty() ? "" : " (" + typeRaw + ")"));
            String from = mdTableCell(formatDateLikeField(lr.get("startDate")));
            String to = mdTableCell(formatDateLikeField(lr.get("endDate")));
            Object td = lr.get("totalDays");
            String days = mdTableCell(td == null ? "—" : strVal(td) + " ngày");
            String st = mdTableCell(leaveStatusLabel(strVal(lr.get("status"))));
            String created = mdTableCell(formatDateLikeField(lr.get("createdAt")));
            sb.append("| ").append(id).append(" | ").append(name).append(" | ").append(type).append(" | ")
                    .append(from).append(" | ").append(to).append(" | ").append(days).append(" | ")
                    .append(st).append(" | ").append(created).append(" |\n");
        }
        Object total = snap.get("totalElements");
        long totalN = total instanceof Number n ? n.longValue() : max;
        sb.append("\nHiện tại, có tổng cộng **").append(totalN).append("** đơn xin nghỉ phép đang chờ bạn xem xét");
        if (total instanceof Number num && num.longValue() > max) {
            sb.append(" (đang hiển thị ").append(max).append(" đơn đầu — vào mục duyệt phép để xử lý đầy đủ)");
        }
        sb.append(".");
        return sb.toString().trim();
    }

    private String formatMgrTeamAttendanceFallback(Map<String, Object> snap) {
        Object contentObj = snap.get("content");
        StringBuilder sb = new StringBuilder();
        String month = strVal(snap.get("month"));
        sb.append("**Chấm công team (đi muộn");
        if (!month.isEmpty()) {
            sb.append(" — ").append(month);
        }
        sb.append(")**\n\n");
        if (!(contentObj instanceof List<?> list) || list.isEmpty()) {
            sb.append("Không có bản ghi đi muộn trong phạm vi hoặc kỳ này.");
            return sb.toString().trim();
        }
        int max = Math.min(list.size(), 20);
        sb.append("| Tên nhân viên | Ngày | Giờ vào |\n");
        sb.append("| --- | --- | --- |\n");
        for (int i = 0; i < max; i++) {
            Map<String, Object> row = snapshotRowToMap(list.get(i));
            String name = mdTableCell(strVal(row.get("employeeName")));
            if (name.isEmpty()) {
                name = mdTableCell("—");
            }
            String d = mdTableCell(formatDateLikeField(row.get("attendanceDate")));
            String inCell = mdTableCell(formatDateTimeField(row.get("checkIn")));
            sb.append("| ").append(name).append(" | ").append(d).append(" | ").append(inCell).append(" |\n");
        }
        return sb.toString().trim();
    }

    private String formatDashboardStatsFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) {
            return String.valueOf(err);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Thống kê nhân sự tháng ").append(strVal(snap.get("generatedAt")).isEmpty()
                ? "hiện tại"
                : strVal(snap.get("generatedAt")).substring(0, 7)).append("**\n\n");

        // Số nhân viên
        Object total = snap.get("totalEmployees");
        if (total != null) {
            sb.append("Tổng số nhân viên: **").append(strVal(total)).append("**\n");
        }
        Object totalDept = snap.get("totalDepartments");
        if (totalDept != null) {
            sb.append("Số phòng ban: **").append(strVal(totalDept)).append("**\n");
        }

        // Phân bổ theo trạng thái nhân viên
        Object byStatus = snap.get("byStatus");
        if (byStatus instanceof Map<?, ?> statusMap && !statusMap.isEmpty()) {
            sb.append("\n**Trạng thái nhân viên**\n");
            statusMap.forEach((k, v) -> {
                String label = switch (String.valueOf(k).toUpperCase(Locale.ROOT)) {
                    case "ACTIVE" -> "Đang làm việc";
                    case "INACTIVE" -> "Đã nghỉ việc";
                    case "ON_LEAVE" -> "Đang nghỉ phép";
                    default -> String.valueOf(k);
                };
                sb.append("- ").append(label).append(": ").append(strVal(v)).append("\n");
            });
        }

        // Chấm công tháng
        Object attMonth = snap.get("attendanceMonth");
        if (attMonth instanceof Map<?, ?> attMap) {
            sb.append("\n**Chấm công tháng ").append(strVal(attMap.get("month"))).append("**\n");
            Object totalRec = attMap.get("totalRecords");
            if (totalRec != null) {
                sb.append("Tổng bản ghi chấm công: **").append(strVal(totalRec)).append("**\n");
            }
            Object rate = attMap.get("attendanceRate");
            if (rate != null) {
                sb.append("Tỷ lệ đi làm: **").append(strVal(rate)).append("%**\n");
            }
            Object byAtt = attMap.get("byStatus");
            if (byAtt instanceof Map<?, ?> attStatusMap && !attStatusMap.isEmpty()) {
                sb.append("\n| Trạng thái | Số bản ghi |\n| --- | --- |\n");
                attStatusMap.forEach((k, v) -> {
                    String label = switch (String.valueOf(k).toUpperCase(Locale.ROOT)) {
                        case "PRESENT" -> "Đúng giờ";
                        case "LATE" -> "Đi muộn";
                        case "ABSENT" -> "Vắng";
                        case "HALF_DAY" -> "Nửa ngày";
                        case "ON_LEAVE" -> "Nghỉ phép";
                        default -> String.valueOf(k);
                    };
                    sb.append("| ").append(label).append(" | ").append(strVal(v)).append(" |\n");
                });
            }
        }

        // Phân bổ theo phòng ban
        Object byDept = snap.get("byDepartment");
        if (byDept instanceof List<?> deptList && !deptList.isEmpty()) {
            sb.append("\n**Nhân viên theo phòng ban**\n\n");
            sb.append("| Phòng ban | Số nhân viên |\n| --- | --- |\n");
            int maxDept = Math.min(deptList.size(), 20);
            for (int i = 0; i < maxDept; i++) {
                Map<String, Object> row = snapshotRowToMap(deptList.get(i));
                String deptName = mdTableCell(strVal(row.get("departmentName")));
                if (deptName.isEmpty())
                    deptName = mdTableCell("Phòng ban #" + strVal(row.get("departmentId")));
                String cnt = mdTableCell(strVal(row.get("count")));
                sb.append("| ").append(deptName).append(" | ").append(cnt).append(" |\n");
            }
        }

        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String formatEmployeeLookupFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) return String.valueOf(err);
        Object notice = snap.get("notice");
        if (notice != null) return String.valueOf(notice);
        Object total = snap.get("totalFound");
        Object keyword = snap.get("keyword");
        Object employees = snap.get("employees");
        StringBuilder sb = new StringBuilder();
        sb.append("**K\u1ebft qu\u1ea3 tra c\u1ee9u nh\u00e2n vi\u00ean**");
        if (keyword != null && !String.valueOf(keyword).isBlank()) {
            sb.append(" (t\u1eeb kh\u00f3a: \"").append(keyword).append("\")");
        }
        sb.append("\n\n");
        if (total != null) {
            sb.append("T\u00ecm th\u1ea5y **").append(strVal(total)).append("** nh\u00e2n vi\u00ean.\n\n");
        }
        if (employees instanceof List<?> list && !list.isEmpty()) {
            sb.append("| H\u1ecd T\u00ean | M\u00e3 NV | Ph\u00f2ng Ban | Ch\u1ee9c V\u1ee5 | Email | S\u0110T | Tr\u1ea1ng Th\u00e1i |\n");
            sb.append("| --- | --- | --- | --- | --- | --- | --- |\n");
            for (Object item : list) {
                Map<String, Object> m = snapshotRowToMap(item);
                sb.append("| ").append(mdTableCell(strVal(m.get("h\u1ecd T\u00ean")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("m\u00e3NV")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("ph\u00f2ng Ban")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("ch\u1ee9c V\u1ee5")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("email")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("s\u0110T")))).append(" | ")
                  .append(mdTableCell(strVal(m.get("tr\u1ea1ngTh\u00e1i")))).append(" |\n");
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String formatEmployeeDetailsFallback(Map<String, Object> snap) {
        Object err = snap.get("error");
        if (err != null) return String.valueOf(err);

        Map<String, Object> emp = toStringKeyMap(snap.get("employee"));
        String name = strVal(emp.get("fullName"));
        String code = strVal(emp.get("employeeCode"));
        String dept = strVal(emp.get("departmentName"));
        String pos = strVal(emp.get("position"));

        StringBuilder sb = new StringBuilder();
        sb.append("**Chi tiết nhân viên**\n\n");
        if (!name.isEmpty() || !code.isEmpty()) {
            sb.append("- ").append(!name.isEmpty() ? name : "Nhân viên").append(code.isEmpty() ? "" : " (" + code + ")").append("\n");
        }
        if (!dept.isEmpty()) sb.append("- Phòng ban: ").append(dept).append("\n");
        if (!pos.isEmpty()) sb.append("- Chức vụ: ").append(pos).append("\n");

        Map<String, Object> att = toStringKeyMap(snap.get("attendanceThisMonth"));
        if (!att.isEmpty()) {
            sb.append("\n**Chấm công tháng ").append(strVal(att.get("month"))).append("**\n");
            sb.append("- Số ngày đi muộn: ").append(strVal(att.get("lateDays"))).append("\n");
            sb.append("- Tổng giờ làm (span): ").append(strVal(att.get("workHours"))).append("\n");
            sb.append("- Tổng OT: ").append(strVal(att.get("overtimeHours"))).append("\n");
        }

        Map<String, Object> leave = toStringKeyMap(snap.get("leaveThisYear"));
        if (!leave.isEmpty()) {
            sb.append("\n**Nghỉ phép năm ").append(strVal(leave.get("year"))).append("**\n");
            sb.append("- Phép năm đã duyệt: ").append(strVal(leave.get("annualLeaveApprovedDays"))).append(" ngày\n");
            sb.append("- Phép năm còn lại (ước tính): ").append(strVal(leave.get("annualLeaveRemainingEstimate"))).append(" ngày\n");
            sb.append("- Nghỉ không lương đã duyệt: ").append(strVal(leave.get("unpaidLeaveApprovedDays"))).append(" ngày\n");
            sb.append("- Nghỉ ốm đã duyệt: ").append(strVal(leave.get("sickLeaveApprovedDays"))).append(" ngày\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotRowToMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?>) {
            return toStringKeyMap(raw);
        }
        try {
            Map<String, Object> m = jsonMapper.convertValue(raw, Map.class);
            return m != null ? m : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String mdTableCell(String text) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        return text.replace('|', '\uFF5C').replace('\n', ' ').trim();
    }

    private static String leaveStatusLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Đang chờ duyệt";
            case "APPROVED" -> "Đã duyệt";
            case "REJECTED" -> "Từ chối";
            default -> raw.trim();
        };
    }

    private static String formatDateLikeField(Object v) {
        if (v == null) {
            return "—";
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi")));
        }
        if (v instanceof LocalDate ld) {
            return ld.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi")));
        }
        if (v instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
            String t = s.trim();
            try {
                if (t.length() >= 19 && t.charAt(10) == ' ') {
                    DateTimeFormatter sp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
                    LocalDateTime ldt = LocalDateTime.parse(t.substring(0, 19), sp);
                    return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi")));
                }
                if (t.length() >= 19 && t.contains("T")) {
                    String iso = t.length() > 19 && t.charAt(19) == '.' ? t.substring(0, 19)
                            : t.substring(0, Math.min(19, t.length()));
                    LocalDateTime ldt = LocalDateTime.parse(iso);
                    return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi")));
                }
                if (t.length() >= 10 && t.charAt(4) == '-' && t.charAt(7) == '-') {
                    return LocalDate.parse(t.substring(0, 10)).format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi")));
                }
            } catch (Exception ignored) {
                // fall through
            }
            return t;
        }
        return formatDateTimeField(v);
    }

    private static String translateAttendanceMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return switch (message.trim()) {
            case "No attendance record for today" -> "Hôm nay chưa có bản ghi chấm công.";
            case "Approved leave today" -> "Hôm nay bạn đang trong ngày nghỉ phép đã được duyệt.";
            default -> message;
        };
    }

    private static String attendanceStatusVi(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PRESENT" -> "Đúng giờ";
            case "LATE" -> "Đi muộn";
            case "ABSENT" -> "Vắng";
            case "HALF_DAY" -> "Nửa ngày";
            case "ON_LEAVE" -> "Nghỉ phép";
            default -> raw;
        };
    }

    private static String leaveStatusVi(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "chờ duyệt";
            case "APPROVED" -> "đã duyệt";
            case "REJECTED" -> "từ chối";
            default -> raw;
        };
    }

    private static String leaveTypeVi(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Nghỉ";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ANNUAL" -> "Phép năm";
            case "SICK" -> "Ốm";
            case "UNPAID" -> "Không lương";
            case "MATERNITY" -> "Thai sản";
            case "OTHER" -> "Khác";
            default -> raw;
        };
    }

    private static String formatDateTimeField(Object v) {
        if (v == null) {
            return "—";
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi")));
        }
        if (v instanceof LocalDate ld) {
            return ld.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi")));
        }
        String s = String.valueOf(v);
        if (s.isBlank() || "null".equalsIgnoreCase(s)) {
            return "—";
        }
        return s;
    }

    private static String formatDecimalField(Object v) {
        if (v == null) {
            return "—";
        }
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Number n) {
            return String.valueOf(n);
        }
        String s = String.valueOf(v);
        return s.isBlank() ? "—" : s;
    }

    private static Map<String, Object> toStringKeyMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    private static String strVal(Object o) {
        if (o == null) {
            return "";
        }
        String s = String.valueOf(o);
        return "null".equals(s) ? "" : s;
    }

}
