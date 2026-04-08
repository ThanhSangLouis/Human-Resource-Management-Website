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
            return trimTo(body, 480);
        }
        int start = Math.max(0, idx - 120);
        int end = Math.min(body.length(), idx + 360);
        String slice = body.substring(start, end).replaceAll("\\s+", " ").trim();
        return trimTo(slice, 480);
    }

    private static String trimTo(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
