package org.example.hrmsystem.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Tạo bảng audit khi {@code ai.audit.auto-ddl=true} (mặc định). Tắt trong test ({@code ai.audit.auto-ddl=false})
 * vì H2 + {@code ddl-auto=create-drop} đã tạo từ entity.
 */
@Component
@Order(5)
public class AiChatAuditSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiChatAuditSchemaInitializer.class);

    private final JdbcTemplate jdbc;
    private final boolean autoDdl;

    public AiChatAuditSchemaInitializer(
            DataSource dataSource,
            @Value("${ai.audit.auto-ddl:true}") boolean autoDdl
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.autoDdl = autoDdl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoDdl) {
            return;
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS ai_chat_audit (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        created_at TIMESTAMP(6) NOT NULL,
                        user_id BIGINT NOT NULL,
                        employee_id BIGINT NULL,
                        role_name VARCHAR(32) NULL,
                        input_hash VARCHAR(64) NOT NULL,
                        input_preview VARCHAR(255) NULL,
                        intent VARCHAR(64) NULL,
                        fallback_flag TINYINT(1) NOT NULL,
                        outcome VARCHAR(32) NOT NULL,
                        http_status INT NULL,
                        error_message VARCHAR(512) NULL,
                        duration_ms BIGINT NULL
                    )
                    """);
            log.info("AI chat audit table ensured (CREATE IF NOT EXISTS)");
        } catch (Exception e) {
            log.warn("Could not ensure ai_chat_audit table (JPA may still manage schema in dev): {}", e.getMessage());
        }
    }
}
