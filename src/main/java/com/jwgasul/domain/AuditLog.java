// AuditLog.java — 감사 로그 엔티티(audit_log, 3.7). 누가·언제·무엇을 바꿨/조회했는지 기록(F-12)
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username; // 수행자

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType; // WORKER / ACCOUNT / DOCUMENT / SITE / ROSTER

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, length = 20)
    private String action; // CREATE / UPDATE / DELETE / VIEW

    @Column(name = "changed_field", length = 50)
    private String changedField;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String username, String entityType, Long entityId, String action,
                    String changedField, String oldValue, String newValue, String ipAddress) {
        this.username = username;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.changedField = changedField;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getChangedField() {
        return changedField;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
