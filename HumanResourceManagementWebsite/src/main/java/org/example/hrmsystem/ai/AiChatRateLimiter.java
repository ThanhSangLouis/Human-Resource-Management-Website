package org.example.hrmsystem.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiChatRateLimiter {

    private final int maxPerMinute;

    private final Map<Long, MinuteBucket> buckets = new ConcurrentHashMap<>();

    public AiChatRateLimiter(
            @Value("${ai.chat.max-messages-per-minute:30}") int maxPerMinute
    ) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
    }

    public boolean allow(long userId) {
        long minute = System.currentTimeMillis() / 60_000;
        MinuteBucket b = buckets.computeIfAbsent(userId, k -> new MinuteBucket());
        return b.record(minute, maxPerMinute);
    }

    private static final class MinuteBucket {
        private volatile long window = -1;
        private volatile int count;

        synchronized boolean record(long minute, int max) {
            if (window != minute) {
                window = minute;
                count = 0;
            }
            if (count >= max) {
                return false;
            }
            count++;
            return true;
        }
    }
}
