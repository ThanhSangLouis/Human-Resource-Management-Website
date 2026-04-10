package org.example.hrmsystem.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Rate limiter dùng MySQL làm backing store — persistent qua restart,
 * hoạt động đúng khi chạy multi-instance.
 *
 * <p>Thuật toán: mỗi cặp (user_id, window_minute) là một row.
 * <ol>
 *   <li>INSERT với msg_count = 1; nếu đã tồn tại thì cộng dồn (ON DUPLICATE KEY UPDATE).</li>
 *   <li>Đọc lại count; nếu > max thì từ chối.</li>
 * </ol>
 * Atomic theo cơ chế InnoDB UPSERT — không cần lock tầng ứng dụng.
 *
 * <p>window_minute = System.currentTimeMillis() / 60_000 (Unix minute, UTC).
 * Row cũ được dọn mỗi đêm bởi {@link #cleanupOldWindows()}.
 *
 * <p><b>Fail-open:</b> nếu bảng chưa tồn tại (môi trường test H2),
 * mọi request đều được phép — không crash app.
 */
@Component
public class AiChatRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiChatRateLimiter.class);

    private final JdbcTemplate jdbc;
    private final int maxPerMinute;

    public AiChatRateLimiter(
            DataSource dataSource,
            @Value("${ai.chat.max-messages-per-minute:30}") int maxPerMinute
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.maxPerMinute = Math.max(1, maxPerMinute);
    }

    /**
     * Kiểm tra và ghi nhận một lượt gửi tin cho {@code userId}.
     *
     * @return {@code true} nếu còn trong hạn mức, {@code false} nếu đã vượt.
     */
    public boolean allow(long userId) {
        long window = currentWindow();
        try {
            // Atomic UPSERT: tạo mới hoặc tăng count
            jdbc.update("""
                    INSERT INTO ai_rate_limit (user_id, window_minute, msg_count)
                    VALUES (?, ?, 1)
                    ON DUPLICATE KEY UPDATE msg_count = msg_count + 1
                    """, userId, window);

            Integer count = jdbc.queryForObject(
                    "SELECT msg_count FROM ai_rate_limit WHERE user_id = ? AND window_minute = ?",
                    Integer.class, userId, window);

            return count != null && count <= maxPerMinute;
        } catch (DataAccessException e) {
            // Bảng chưa tồn tại (test) hoặc DB tạm thời không sẵn sàng → fail-open
            log.debug("AI rate-limit DB error (fail-open): {}", e.getMessage());
            return true;
        }
    }

    /** Unix minute hiện tại (UTC). */
    private static long currentWindow() {
        return System.currentTimeMillis() / 60_000L;
    }

    /**
     * Xóa các row cũ hơn 2 phút — chạy lúc 02:00 hàng ngày.
     * Tránh bảng phình to vô hạn.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldWindows() {
        long threshold = currentWindow() - 2;
        try {
            int deleted = jdbc.update(
                    "DELETE FROM ai_rate_limit WHERE window_minute < ?", threshold);
            if (deleted > 0) {
                log.info("AI rate-limit cleanup: deleted {} stale rows (window < {})", deleted, threshold);
            }
        } catch (DataAccessException e) {
            log.warn("AI rate-limit cleanup failed: {}", e.getMessage());
        }
    }
}
