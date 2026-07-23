// Site.java — 현장 엔티티(site 테이블, 3.5). 종료는 삭제가 아니라 is_active=false(soft) 로 처리.
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "client_name", length = 100)
    private String clientName;

    @Column(length = 255)
    private String address;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Site() {
    }

    public Site(String name, String clientName, String address, LocalDate startDate, LocalDate endDate,
                boolean active, String memo) {
        this.name = name;
        this.clientName = clientName;
        this.address = address;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.memo = memo;
    }

    // 현장 정보 수정
    public void update(String name, String clientName, String address, LocalDate startDate, LocalDate endDate,
                       boolean active, String memo) {
        this.name = name;
        this.clientName = clientName;
        this.address = address;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.memo = memo;
    }

    // 진행 여부 전환(종료/재개)
    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getClientName() {
        return clientName;
    }

    public String getAddress() {
        return address;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
