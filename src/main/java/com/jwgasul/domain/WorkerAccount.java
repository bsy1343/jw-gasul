// WorkerAccount.java — 근로자 계좌 엔티티(worker_account, 3.3). 인원당 최대 3개, 주계좌 1개.
// 계좌번호는 하이픈 제외 평문 저장 — 화면 마스킹 + 접근 감사로 보호(우회 금지).
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "worker_account")
public class WorkerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "bank_name", nullable = false, length = 30)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber; // 하이픈 제외 숫자만 저장

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder = 1;

    @Column(length = 100)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkerAccount() {
    }

    public WorkerAccount(Long workerId, String bankName, String accountNumber, String accountHolder,
                         boolean primary, short sortOrder, String memo) {
        this.workerId = workerId;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.primary = primary;
        this.sortOrder = sortOrder;
        this.memo = memo;
    }

    // 계좌 정보 수정(계좌번호 포함)
    public void update(String bankName, String accountNumber, String accountHolder, String memo) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.memo = memo;
    }

    // 주계좌 지정/해제
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public boolean isPrimary() {
        return primary;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public String getMemo() {
        return memo;
    }
}
