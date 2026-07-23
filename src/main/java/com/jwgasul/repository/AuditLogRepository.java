// AuditLogRepository.java — 감사 로그 조회/저장/보관정리 리포지토리(3.7, F-12)
package com.jwgasul.repository;

import com.jwgasul.domain.AuditLog;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // 특정 엔티티의 변경 이력(근로자 상세 하단 표시용)
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    // 근로자 상세 변경 이력(근로자 + 그 인원의 서류 변경)
    List<AuditLog> findByEntityTypeInAndEntityIdOrderByCreatedAtDesc(Collection<String> entityTypes, Long entityId);

    // 보관 기간 경과 정리(배치, 1년)
    long deleteByCreatedAtBefore(Instant cutoff);
}
