package org.example.hrmsystem.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache câu trả lời khi không có lịch sử hội thoại (virtual proxy giống tinh thần OneShop).
 */
@Component
public class GeminiClientProxy {

    private final GeminiClient delegate;
    private final boolean cacheEnabled;
    private final long ttlSeconds;
    private final int maxEntries;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GeminiClientProxy(
            GeminiClient delegate,
            @Value("${gemini.proxy.cache.enabled:true}") boolean cacheEnabled,
            @Value("${gemini.proxy.cache.ttl-seconds:120}") long ttlSeconds,
            @Value("${gemini.proxy.cache.max-entries:256}") int maxEntries
    ) {
        this.delegate = delegate;
        this.cacheEnabled = cacheEnabled;
        this.ttlSeconds = Math.max(1, ttlSeconds);
        this.maxEntries = Math.max(8, maxEntries);
    }

    public Optional<String> generate(
            String systemInstruction,
            String userPayload,
            boolean hasConversationHistory
    ) {
        if (!cacheEnabled || hasConversationHistory || !delegate.isConfigured()) {
            return delegate.generateContent(systemInstruction, userPayload);
        }
        String key = sha256(systemInstruction + "\n" + userPayload);
        Instant now = Instant.now();
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(now)) {
            return Optional.ofNullable(hit.text());
        }
        if (hit != null) {
            cache.remove(key);
        }
        Optional<String> out = delegate.generateContent(systemInstruction, userPayload);
        out.ifPresent(text -> put(key, text, now));
        return out;
    }

    private void put(String key, String text, Instant now) {
        if (cache.size() >= maxEntries) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(text, now.plusSeconds(ttlSeconds)));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private record CacheEntry(String text, Instant expires) {
    }
}
