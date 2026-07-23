// Roster.java — 명부 이력 엔티티(roster, 3.6). 현장 선택 시 site_id, 미등록 현장은 title로 대체.
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "roster")
public class Roster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id")
    private Long siteId; // 등록 현장이면 값, 임시 현장이면 null

    @Column(nullable = false, length = 100)
    private String title; // 현장명 또는 임시 제목

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RosterType type;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Roster() {
    }

    public Roster(Long siteId, String title, LocalDate targetDate, RosterType type, String createdBy) {
        this.siteId = siteId;
        this.title = title;
        this.targetDate = targetDate;
        this.type = type;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public RosterType getType() {
        return type;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
