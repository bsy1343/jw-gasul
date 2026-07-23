// AuditLogRepository.java — 감사 로그 조회/저장 리포지토리(3.7). 조회 화면(/audit)은 Stage 9.
package com.jwgasul.repository;

import com.jwgasul.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 특정 엔티티의 변경 이력(근로자 상세 하단 표시용)
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
