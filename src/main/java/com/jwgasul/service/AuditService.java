// AuditService.java — 감사 로그 기록(쓰기)(F-12). 수행자(SecurityContext)·실 IP(CF-Connecting-IP)를 자동 주입.
// 조회 화면(/audit)·보관 배치는 Stage 9. 지금은 계좌 조회/변경 등 민감 이벤트 기록에 사용.
package com.jwgasul.service;

import com.jwgasul.domain.AuditLog;
import com.jwgasul.dto.AuditFilter;
import com.jwgasul.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final String trustedHeader;

    public AuditService(
            AuditLogRepository auditLogRepository,
            @Value("${app.security.trusted-header:CF-Connecting-IP}") String trustedHeader) {
        this.auditLogRepository = auditLogRepository;
        this.trustedHeader = trustedHeader;
    }

    // 감사 이벤트 1건 기록(변경 필드/전후값은 없으면 null)
    @Transactional
    public void log(String entityType, Long entityId, String action,
                    String changedField, String oldValue, String newValue) {
        auditLogRepository.save(new AuditLog(
                currentUsername(), entityType, entityId, action,
                changedField, trim(oldValue), trim(newValue), currentIp()));
    }

    // CREATE/DELETE 등 필드 없는 단순 이벤트
    @Transactional
    public void log(String entityType, Long entityId, String action) {
        log(entityType, entityId, action, null, null, null);
    }

    // 특정 엔티티의 변경 이력(최신순) — 상세 화면 표시용
    @Transactional(readOnly = true)
    public List<AuditLog> history(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    // 근로자 상세 변경 이력(근로자 본인 + 그 인원의 서류 변경)
    @Transactional(readOnly = true)
    public List<AuditLog> workerHistory(Long workerId) {
        return auditLogRepository.findByEntityTypeInAndEntityIdOrderByCreatedAtDesc(List.of("WORKER", "DOCUMENT"), workerId);
    }

    // 감사 로그 조회(F-12): 기간·사용자·대상 필터
    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditFilter f, Pageable pageable) {
        Specification<AuditLog> spec = (r, q, cb) -> cb.conjunction();
        if (f.getFrom() != null) {
            Instant start = f.getFrom().atStartOfDay(ZoneId.systemDefault()).toInstant();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), start));
        }
        if (f.getTo() != null) {
            Instant end = f.getTo().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), end));
        }
        if (f.getUsername() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("username"), f.getUsername()));
        }
        if (f.getEntityType() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), f.getEntityType()));
        }
        return auditLogRepository.findAll(spec, pageable);
    }

    // 보관 기간(1년) 경과 로그 정리(F-12) — 배치에서 호출
    @Transactional
    public long purgeOlderThan(Instant cutoff) {
        return auditLogRepository.deleteByCreatedAtBefore(cutoff);
    }

    // 현재 로그인 사용자명(없으면 null)
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
    }

    // 실제 클라이언트 IP — CF-Connecting-IP(없으면 X-Forwarded-For 첫 항, 없으면 remoteAddr)
    private String currentIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getHeader(trustedHeader);
        if (!StringUtils.hasText(ip)) {
            String xff = req.getHeader("X-Forwarded-For");
            ip = StringUtils.hasText(xff) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        }
        return ip;
    }

    // audit_log 값 컬럼은 VARCHAR(255) — 초과분 절단
    private String trim(String v) {
        if (v == null) {
            return null;
        }
        return v.length() > 255 ? v.substring(0, 255) : v;
    }
}
