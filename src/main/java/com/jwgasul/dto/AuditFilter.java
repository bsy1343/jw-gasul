// AuditFilter.java — 감사 로그 조회 필터(F-12): 기간·사용자·대상
package com.jwgasul.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class AuditFilter {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private String username;

    private String entityType; // WORKER / ACCOUNT / DOCUMENT / ROSTER / SITE

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = (username == null || username.isBlank()) ? null : username.trim();
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = (entityType == null || entityType.isBlank()) ? null : entityType;
    }
}
