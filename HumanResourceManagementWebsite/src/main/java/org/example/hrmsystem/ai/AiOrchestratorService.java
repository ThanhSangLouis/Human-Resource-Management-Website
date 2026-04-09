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

    private static final String SYSTEM_INSTRUCTION = """
            Bạn là trợ lý HR nội bộ cho ứng dụng HRM. Chỉ trả lời dựa trên factsJson và policyExcerpts trong payload (và priorTurns nếu có).
            Ưu tiên: (1) trích và giải thích đầy đủ quy định trongtrúc (đoạn ngắn, gạch đầu dòng), không quá sơ  policy khi câu hỏi về luật lệ/quy định; (2) sau đó mới bổ sung facts cá nhân nếu liên quan.
            Trả lời chi tiết, có cấu sài. Nếu thiếu dữ liệu trong payload, nói rõ phần nào hệ thống không có — không bịa.
            Tôn trọng trường dataAccessScope: không mô tả dữ liệu nhân viên khác hoặc phòng ban khác nếu scope không cho phép; không vượt quyền so với userRole.
            Nếu user viết tiếng Việt thì trả lời tiếng Việt; nếu tiếng Anh thì tiếng Anh.
            Không nhắc tên file policy, mã biến cấu hình hay chuỗi kỹ thuật (ví dụ *.md, ai.*). Trình bày như nhân sự nói với nhân viên.
            Khi intent là MGR_PENDING_LEAVE hoặc MGR_TEAM_ATTENDANCE và factsJson.content là mảng nhiều bản ghi: luôn trình bày bằng một bảng Markdown (một dòng tiêu đề cột, một dòng phân cách |---|---|---|, mỗi bản ghi một hàng). Không liệt kê từng đơn theo kiểu nhiều dòng * nhãn / giá trị lặp lại như một form dọc.""";

    private final AiGuardrailService guardrail;
    private final AiIntentClassifier intentClassifier;
    private final PolicyKnowledgeService policyKnowledgeService;
    private final HrAiToolService hrAiToolService;
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
            GeminiClient geminiClient,
            GeminiClientProxy geminiClientProxy,
            AiChatRateLimiter rateLimiter,
            JsonMapper jsonMapper,
            @Value("${ai.gemini.enabled:true}") boolean geminiEnabled
    ) {
        this.guardrail = guardrail;
        this.intentClassifier = intentClassifier;
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

        AiIntent intent = intentClassifier.classify(message);
        if (intentClassifier.managerIntents().contains(intent) && Role.EMPLOYEE.name().equals(principal.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ quản lý / HR / Admin mới xem được dữ liệu team."
            );
        }

        List<AiCitationDto> citations = new ArrayList<>();
        if (intent == AiIntent.FAQ) {
            citations.addAll(policyKnowledgeService.findRelevantWithFallback(message, 4));
        }

        Map<String, Object> dataSnapshot = hrAiToolService.runTools(intent, principal, message);
        String policyBlock = policyKnowledgeService.combinedExcerptText(citations);

        boolean hasHistory = request.getHistory() != null && !request.getHistory().isEmpty();
        String userPayload = buildUserPayload(principal, intent, message, dataSnapshot, policyBlock, hasHistory, request);

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
            AppUserDetails principal,
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
        final String hrAdmin =
                "Có thể xem hàng đợi rộng / lọc theo facts; vẫn chỉ dựa trên factsJson, không bịa thêm nhân viên hay số liệu.";
        return switch (r) {
            case EMPLOYEE -> mgrIntent
                    ? "Không hợp lệ cho EMPLOYEE (không có facts team)."
                    : (intent == AiIntent.FAQ
                    ? "Chỉ policy chung + facts cá nhân nếu có trong payload; không suy diễn dữ liệu người khác."
                    : "factsJson chỉ phạm vi cá nhân (chấm công/phép/thông báo của user đăng nhập).");
            case MANAGER -> mgrIntent
                    ? "factsJson có thể gồm đơn chờ duyệt / đi muộn trong phạm vi team được quản lý; không toàn công ty trừ khi facts nói vậy."
                    : "Tự phục vụ: dữ liệu cá nhân của manager; FAQ: policy chung.";
            case HR -> hrAdmin;
            case ADMIN -> hrAdmin;
        };
    }

    private String buildFallbackReply(
            AiIntent intent,
            Map<String, Object> dataSnapshot,
            List<AiCitationDto> citations,
            boolean networkOff
    ) {
        String body = switch (intent) {
            case FAQ -> formatFaqFallback(citations);
            case SELF_ATTENDANCE -> formatSelfAttendanceFallback(dataSnapshot);
            case SELF_LEAVE -> formatSelfLeaveFallback(dataSnapshot);
            case SELF_NOTIFICATIONS, NOTIF_SUMMARY -> formatSelfNotificationsFallback(dataSnapshot);
            case MGR_PENDING_LEAVE -> formatMgrPendingFallback(dataSnapshot);
            case MGR_TEAM_ATTENDANCE -> formatMgrTeamAttendanceFallback(dataSnapshot);
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

    /** Tiêu đề hiển thị theo tên file, tiếng Việt — tránh chữ attendance/leave khô khan. */
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

    /** Tránh lặp tiêu đề: excerpt đã có # ... trùng với friendlyDocTitle thì bỏ dòng đó. */
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
     * Bỏ #/## trong excerpt, chuyển thành dòng in đậm; giữ gạch đầu dòng — dễ render HTML phía client.
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
            sb.append("\n(Còn ").append(num.longValue() - n).append(" bản ghi khác trong tháng — xem đầy đủ tại mục chấm công.)");
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
        sb.append("| Mã đơn | Tên nhân viên | Loại phép | Từ ngày | Đến ngày | Số ngày | Trạng thái | Ngày tạo đơn |\n");
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
                    String iso = t.length() > 19 && t.charAt(19) == '.' ? t.substring(0, 19) : t.substring(0, Math.min(19, t.length()));
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
