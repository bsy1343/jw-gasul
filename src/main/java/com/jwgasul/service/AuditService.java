// AuditService.java — 감사 로그 기록(쓰기)(F-12). 수행자(SecurityContext)·실 IP(CF-Connecting-IP)를 자동 주입.
// 조회 화면(/audit)·보관 배치는 Stage 9. 지금은 계좌 조회/변경 등 민감 이벤트 기록에 사용.
package com.jwgasul.service;

import com.jwgasul.domain.AuditLog;
import com.jwgasul.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
