// Worker.java — 근로자 엔티티(worker 테이블, 3.1). 비자/교육 유효기간·soft delete 포함
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "worker")
public class Worker {

    // 비자 만료일 미상/해당없음(한국인)을 나타내는 관례값. 화면·집계에서 '-'로 처리(3.1)
    public static final LocalDate VISA_NO_EXPIRE = LocalDate.of(9999, 12, 31);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "worker_type", nullable = false, length = 10)
    private WorkerType workerType;

    @Column(name = "name_ko", nullable = false, length = 50)
    private String nameKo;

    @Column(name = "name_foreign", length = 100)
    private String nameForeign;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 50)
    private String nationality;

    @Column(name = "visa_grade", length = 20)
    private String visaGrade;

    @Column(name = "visa_expire_date", nullable = false)
    private LocalDate visaExpireDate = VISA_NO_EXPIRE;

    @Column(name = "edu_complete_date")
    private LocalDate eduCompleteDate;

    @Column(name = "edu_expire_date")
    private LocalDate eduExpireDate;

    @Column(name = "is_fixed", nullable = false)
    private boolean fixed = false;

    @Column(columnDefinition = "text")
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 서비스 계층(다른 패키지)에서 생성 후 필드 설정. JPA 기본 생성자 겸용이라 public.
    public Worker() {
    }

    // 비자 만료일 추적 대상인지(9999-12-31이면 해당없음)
    public boolean isVisaTracked() {
        return visaExpireDate != null && !VISA_NO_EXPIRE.equals(visaExpireDate);
    }

    // 비자 만료 상태(F-04) — 화면 배지·집계용. 9999-12-31이면 NONE.
    public ExpiryStatus getVisaStatus() {
        return ExpiryStatus.of(visaExpireDate, !isVisaTracked(), LocalDate.now());
    }

    // 교육 만료 상태(F-04). 만료일이 없으면 UNREGISTERED(미등록).
    public ExpiryStatus getEduStatus() {
        return ExpiryStatus.of(eduExpireDate, false, LocalDate.now());
    }

    // 비자 잔여일(D-day). 추적 대상이 아니면 null.
    public Long getVisaDday() {
        return isVisaTracked() ? ChronoUnit.DAYS.between(LocalDate.now(), visaExpireDate) : null;
    }

    // 교육 잔여일(D-day). 만료일이 없으면 null.
    public Long getEduDday() {
        return eduExpireDate != null ? ChronoUnit.DAYS.between(LocalDate.now(), eduExpireDate) : null;
    }

    // 교육 듣기 링크용 생년월일(yyyyMMdd)(F-10)
    public String getEduLinkBirth() {
        return birthDate == null ? "" : birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    // 화면 표시용 연락처(010-1234-5678). 저장값은 숫자만이므로 자릿수에 맞춰 하이픈을 넣는다.
    // 형식이 예상과 다르면(가공 실패) 원본을 그대로 돌려준다. 교육 링크는 원본 phone을 써야 한다.
    public String getPhoneFormatted() {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11) {                       // 010-1234-5678
            return digits.replaceFirst("(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3");
        }
        if (digits.length() == 10) {                       // 02-1234-5678 / 031-123-4567
            return digits.startsWith("02")
                    ? digits.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "$1-$2-$3")
                    : digits.replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3");
        }
        if (digits.length() == 9 && digits.startsWith("02")) {  // 02-123-4567
            return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{4})", "$1-$2-$3");
        }
        return phone;
    }

    // soft delete 처리
    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    public Long getId() {
        return id;
    }

    public WorkerType getWorkerType() {
        return workerType;
    }

    public void setWorkerType(WorkerType workerType) {
        this.workerType = workerType;
    }

    public String getNameKo() {
        return nameKo;
    }

    public void setNameKo(String nameKo) {
        this.nameKo = nameKo;
    }

    public String getNameForeign() {
        return nameForeign;
    }

    public void setNameForeign(String nameForeign) {
        this.nameForeign = nameForeign;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getVisaGrade() {
        return visaGrade;
    }

    public void setVisaGrade(String visaGrade) {
        this.visaGrade = visaGrade;
    }

    public LocalDate getVisaExpireDate() {
        return visaExpireDate;
    }

    public void setVisaExpireDate(LocalDate visaExpireDate) {
        this.visaExpireDate = visaExpireDate;
    }

    public LocalDate getEduCompleteDate() {
        return eduCompleteDate;
    }

    public void setEduCompleteDate(LocalDate eduCompleteDate) {
        this.eduCompleteDate = eduCompleteDate;
    }

    public LocalDate getEduExpireDate() {
        return eduExpireDate;
    }

    public void setEduExpireDate(LocalDate eduExpireDate) {
        this.eduExpireDate = eduExpireDate;
    }

    public boolean isFixed() {
        return fixed;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
