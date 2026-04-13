package org.example.hrmsystem.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu ngữ cảnh hội thoại tối thiểu theo userId để hỗ trợ các câu follow-up kiểu
 * "cần chi tiết hơn" sau khi vừa tra cứu nhân viên.
 *
 * In-memory: phù hợp MVP / demo. Nếu cần scale multi-node, chuyển sang Redis.
 */
@Service
public class AiConversationContextService {

    public record EmployeeLookupContext(Long employeeId, String employeeName, long updatedAtEpochMs) {}

    private final Map<Long, EmployeeLookupContext> lastLookupByUser = new ConcurrentHashMap<>();
    private final long ttlMs;

    public AiConversationContextService(
            @Value("${ai.context.ttl-seconds:600}") long ttlSeconds
    ) {
        this.ttlMs = Math.max(30_000L, Duration.ofSeconds(Math.max(1, ttlSeconds)).toMillis());
    }

    public void rememberLastEmployeeLookup(Long userId, Long employeeId, String employeeName) {
        if (userId == null || employeeId == null) {
            return;
        }
        lastLookupByUser.put(userId, new EmployeeLookupContext(employeeId, employeeName, System.currentTimeMillis()));
    }

    public Optional<EmployeeLookupContext> getLastEmployeeLookup(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        EmployeeLookupContext ctx = lastLookupByUser.get(userId);
        if (ctx == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() - ctx.updatedAtEpochMs() > ttlMs) {
            lastLookupByUser.remove(userId);
            return Optional.empty();
        }
        return Optional.of(ctx);
    }
}

