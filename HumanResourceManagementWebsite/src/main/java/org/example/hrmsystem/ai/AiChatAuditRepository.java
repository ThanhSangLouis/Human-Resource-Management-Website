package org.example.hrmsystem.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiChatAuditRepository extends JpaRepository<AiChatAudit, Long> {
    long countByUserId(Long userId);

    Optional<AiChatAudit> findFirstByUserIdOrderByIdDesc(Long userId);
}
