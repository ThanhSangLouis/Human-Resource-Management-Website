package org.example.hrmsystem.ai;

import org.example.hrmsystem.ai.dto.AiCitationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PolicyKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(PolicyKnowledgeService.class);

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final Map<String, String> documents = new LinkedHashMap<>();

    @PostConstruct
    void loadPolicies() {
        try {
            Resource[] resources = resolver.getResources("classpath:ai/policy/*.md");
            for (Resource r : resources) {
                if (!r.isReadable()) {
                    continue;
                }
                String name = r.getFilename() != null ? r.getFilename() : r.getDescription();
                String body = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
                documents.put(name, body);
                log.info("Loaded policy snippet: {}", name);
            }
        } catch (IOException e) {
            log.warn("Could not load ai/policy/*.md: {}", e.getMessage());
        }
    }

    private enum PolicyScope {
        ATTENDANCE,
        LEAVE,
        GENERAL,
        MULTI
    }

    /**
     * Tìm đoạn policy liên quan; nếu không khớp từ khóa vẫn trả về vài tài liệu mặc định (tránh FAQ trống).
     * Câu hỏi hẹp (ví dụ chỉ về chấm công hoặc chỉ về nghỉ phép) chỉ lấy đúng một file tương ứng, tránh gộp cả bộ policy.
     */
    public List<AiCitationDto> findRelevantWithFallback(String query, int maxDocs) {
        if (documents.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(maxDocs, 2));
        PolicyScope scope = detectPolicyScope(query);
        switch (scope) {
            case ATTENDANCE -> {
                List<AiCitationDto> one = citationsFromSingleFile("attendance.md", query);
                if (!one.isEmpty()) {
                    return one;
                }
            }
            case LEAVE -> {
                List<AiCitationDto> one = citationsFromSingleFile("leave.md", query);
                if (!one.isEmpty()) {
                    return one;
                }
            }
            case GENERAL -> {
                List<AiCitationDto> one = citationsFromSingleFile("general.md", query);
                if (!one.isEmpty()) {
                    return one;
                }
            }
            case MULTI -> {
                // ranked theo điểm, tối đa 2 tài liệu
            }
        }
        List<AiCitationDto> ranked = findRelevant(query, cap);
        if (!ranked.isEmpty()) {
            return ranked;
        }
        return fallbackPolicyDocs(query, cap);
    }

    private PolicyScope detectPolicyScope(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        boolean att = containsAttendanceSignals(q);
        boolean lea = containsLeaveSignals(q);
        boolean gen = containsGeneralSignals(q);
        int topics = (att ? 1 : 0) + (lea ? 1 : 0) + (gen ? 1 : 0);
        if (topics >= 2) {
            return PolicyScope.MULTI;
        }
        if (att) {
            return PolicyScope.ATTENDANCE;
        }
        if (lea) {
            return PolicyScope.LEAVE;
        }
        if (gen) {
            return PolicyScope.GENERAL;
        }
        return PolicyScope.MULTI;
    }

    private static boolean containsAttendanceSignals(String q) {
        return q.contains("chấm công")
                || q.contains("cham cong")
                || q.contains("check-in")
                || q.contains("check in")
                || q.contains("check-out")
                || q.contains("checkout")
                || q.contains("đi muộn")
                || q.contains("di muon")
                || q.contains("ca làm")
                || q.contains("ca lam")
                || q.contains("attendance")
                || q.contains("đi trễ")
                || q.contains("di tre");
    }

    private static boolean containsLeaveSignals(String q) {
        return q.contains("nghỉ phép")
                || q.contains("nghi phep")
                || q.contains("xin phép")
                || q.contains("xin phep")
                || q.contains("đơn nghỉ")
                || q.contains("don nghi")
                || q.contains("phép ốm")
                || q.contains("phep om")
                || q.contains("phép không lương")
                || q.contains("không lương")
                || q.contains("thai sản")
                || q.contains("maternity")
                || q.contains(" annual")
                || q.startsWith("leave ")
                || q.contains(" leave");
    }

    private static boolean containsGeneralSignals(String q) {
        return q.contains("nguyên tắc chung")
                || q.contains("nguyen tac chung")
                || q.contains("giới hạn của trợ lý")
                || q.contains("gioi han cua tro ly")
                || (q.contains("trợ lý") && (q.contains(" ai") || q.contains("hrm")))
                || (q.contains("tro ly") && q.contains("ai"))
                || (q.contains("phạm vi") && q.contains("vai trò"))
                || (q.contains("pham vi") && q.contains("vai tro"));
    }

    private List<AiCitationDto> citationsFromSingleFile(String filename, String query) {
        String body = documents.get(filename);
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        String excerpt;
        if (tokens.isEmpty()) {
            excerpt = trimTo(normalizePolicyWhitespace(body).trim(), 3200);
        } else {
            excerpt = excerptAroundMatch(body, tokens);
            if (excerpt.length() < 400 && body.length() > excerpt.length()) {
                excerpt = trimTo(normalizePolicyWhitespace(body).trim(), 3200);
            }
        }
        return List.of(new AiCitationDto(filename, excerpt));
    }

    public List<AiCitationDto> findRelevant(String query, int maxDocs) {
        if (documents.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        record Scored(String name, String body, int score) {}
        List<Scored> ranked = documents.entrySet().stream()
                .map(e -> new Scored(e.getKey(), e.getValue(), scoreBody(tokens, e.getValue())))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(Math.max(1, maxDocs))
                .toList();

        List<AiCitationDto> out = new ArrayList<>();
        for (Scored s : ranked) {
            String excerpt = excerptAroundMatch(s.body(), tokens);
            out.add(new AiCitationDto(s.name(), excerpt));
        }
        return out;
    }

    private List<AiCitationDto> fallbackPolicyDocs(String query, int maxDocs) {
        String q = query.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, String>> entries = new ArrayList<>(documents.entrySet());
        entries.sort(Comparator.comparingInt(e -> -guessDocPriority(e.getKey(), q)));
        List<AiCitationDto> out = new ArrayList<>();
        for (Map.Entry<String, String> e : entries) {
            if (out.size() >= maxDocs) {
                break;
            }
            String body = e.getValue();
            out.add(new AiCitationDto(e.getKey(), trimTo(normalizePolicyWhitespace(body).trim(), 2400)));
        }
        return out;
    }

    private static int guessDocPriority(String filename, String qLower) {
        String f = filename.toLowerCase(Locale.ROOT);
        int p = 0;
        if (f.contains("attendance") && (qLower.contains("chấm") || qLower.contains("cham")
                || qLower.contains("công") || qLower.contains("cong") || qLower.contains("muộn")
                || qLower.contains("muon") || qLower.contains("check"))) {
            p += 20;
        }
        if (f.contains("leave") && (qLower.contains("phép") || qLower.contains("phep")
                || qLower.contains("nghỉ") || qLower.contains("nghi") || qLower.contains("leave"))) {
            p += 20;
        }
        if (f.contains("general")) {
            p += 5;
        }
        return p;
    }

    public String combinedExcerptText(List<AiCitationDto> citations) {
        if (citations == null || citations.isEmpty()) {
            return "";
        }
        return citations.stream()
                .map(c -> "[" + c.getSource() + "]\n" + c.getExcerpt())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private static List<String> tokenize(String q) {
        return Arrays.stream(q.toLowerCase(Locale.ROOT).split("\\s+"))
                .map(String::trim)
                .filter(t -> t.length() > 1)
                .toList();
    }

    private static int scoreBody(List<String> tokens, String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String t : tokens) {
            if (lower.contains(t)) {
                score += t.length();
            }
        }
        return score;
    }

    private static String excerptAroundMatch(String body, List<String> tokens) {
        String lower = body.toLowerCase(Locale.ROOT);
        int idx = -1;
        for (String t : tokens) {
            int i = lower.indexOf(t);
            if (i >= 0) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return trimTo(normalizePolicyWhitespace(body), 2200);
        }
        int start = Math.max(0, idx - 200);
        int lineBreak = body.lastIndexOf('\n', idx);
        if (lineBreak >= 0 && idx - lineBreak < 400) {
            start = lineBreak + 1;
        }
        int end = Math.min(body.length(), idx + 1200);
        String slice = body.substring(start, end);
        return trimTo(normalizePolicyWhitespace(slice).trim(), 2200);
    }

    /** Giữ xuống dòng; chỉ gom khoảng trắng trong cùng một dòng (tránh một dòng dài như cũ). */
    private static String normalizePolicyWhitespace(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String[] lines = s.replace("\r\n", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("[ \\t]+", " ").trim();
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().replaceAll("\n{4,}", "\n\n\n");
    }

    private static String trimTo(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
