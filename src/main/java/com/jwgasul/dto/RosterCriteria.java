// RosterCriteria.java — 랜덤 명부 생성 조건 바인딩 DTO(F-06)
package com.jwgasul.dto;

import com.jwgasul.domain.WorkerType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class RosterCriteria {

    private Long siteId;              // 등록 현장 선택(없으면 title 사용)
    private String title;             // 임시 현장 제목

    @NotNull(message = "날짜를 입력하세요")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetDate;

    @Min(value = 1, message = "필요 인원은 1명 이상")
    private int count = 1;

    private WorkerType type;          // 대상: 전체(null) / FOREIGN / KOREAN

    // 제외 옵션(비자 만료자·교육 만료자 제외는 기본 ON — 현장 투입 불가 인원이라 기본 배제)
    private boolean excludeVisaExpired = true;
    private boolean excludeVisaImminent;
    private boolean excludeEduExpired = true;
    private boolean excludeMissingDoc;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public WorkerType getType() {
        return type;
    }

    public void setType(WorkerType type) {
        this.type = type;
    }

    public boolean isExcludeVisaExpired() {
        return excludeVisaExpired;
    }

    public void setExcludeVisaExpired(boolean excludeVisaExpired) {
        this.excludeVisaExpired = excludeVisaExpired;
    }

    public boolean isExcludeVisaImminent() {
        return excludeVisaImminent;
    }

    public void setExcludeVisaImminent(boolean excludeVisaImminent) {
        this.excludeVisaImminent = excludeVisaImminent;
    }

    public boolean isExcludeEduExpired() {
        return excludeEduExpired;
    }

    public void setExcludeEduExpired(boolean excludeEduExpired) {
        this.excludeEduExpired = excludeEduExpired;
    }

    public boolean isExcludeMissingDoc() {
        return excludeMissingDoc;
    }

    public void setExcludeMissingDoc(boolean excludeMissingDoc) {
        this.excludeMissingDoc = excludeMissingDoc;
    }
}
