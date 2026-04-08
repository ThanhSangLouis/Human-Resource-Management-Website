package org.example.hrmsystem.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public GeminiClient(
            JsonMapper jsonMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model.name:gemini-2.5-flash}") String model,
            @Value("${gemini.max.tokens:1000}") int maxTokens,
            @Value("${gemini.temperature:0.7}") double temperature,
            @Value("${ai.gemini.timeout-ms:45000}") long timeoutMs
    ) {
        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        int t = (int) Math.min(Math.max(timeoutMs, 1), Integer.MAX_VALUE);
        rf.setConnectTimeout(t);
        rf.setReadTimeout(t);
        this.restClient = RestClient.builder()
                .baseUrl(BASE)
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Gọi v1beta generateContent (cùng hướng OneShop): query ?key=, body generationConfig + safetySettings.
     */
    public Optional<String> generateContent(String systemInstruction, String userText) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String path = "/v1beta/models/" + model + ":generateContent";
        ObjectNode root = jsonMapper.createObjectNode();

        ObjectNode sys = jsonMapper.createObjectNode();
        ArrayNode sysParts = jsonMapper.createArrayNode();
        sysParts.add(jsonMapper.createObjectNode().put("text", systemInstruction));
        sys.set("parts", sysParts);
        root.set("systemInstruction", sys);

        ArrayNode contents = jsonMapper.createArrayNode();
        ObjectNode userTurn = jsonMapper.createObjectNode();
        userTurn.put("role", "user");
        ArrayNode userParts = jsonMapper.createArrayNode();
        userParts.add(jsonMapper.createObjectNode().put("text", userText));
        userTurn.set("parts", userParts);
        contents.add(userTurn);
        root.set("contents", contents);

        ObjectNode gen = jsonMapper.createObjectNode();
        gen.put("maxOutputTokens", maxTokens);
        gen.put("temperature", temperature);
        root.set("generationConfig", gen);

        root.set("safetySettings", safetySettingsArray());

        try {
            String body = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("key", apiKey)
                            .build())
                    .body(root.toString())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode tree = jsonMapper.readTree(body);
            if (tree.has("promptFeedback")) {
                JsonNode pf = tree.get("promptFeedback");
                if (pf.has("blockReason")) {
                    log.warn("Gemini blocked: {}", pf.get("blockReason").asText());
                    return Optional.empty();
                }
            }
            JsonNode candidates = tree.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini no candidates: {}", body.length() > 300 ? body.substring(0, 300) : body);
                return Optional.empty();
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return Optional.empty();
            }
            String text = parts.get(0).path("text").asText("");
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (RestClientException e) {
            log.warn("Gemini HTTP error: {}", e.getMessage());
            return Optional.empty();
        } catch (JacksonException e) {
            log.warn("Gemini parse error: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Gemini parse error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ArrayNode safetySettingsArray() {
        ArrayNode arr = jsonMapper.createArrayNode();
        List<Map<String, String>> defs = List.of(
                Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE")
        );
        for (Map<String, String> m : defs) {
            ObjectNode n = jsonMapper.createObjectNode();
            n.put("category", m.get("category"));
            n.put("threshold", m.get("threshold"));
            arr.add(n);
        }
        return arr;
    }
}
