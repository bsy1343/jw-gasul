// WorkerFilter.java — 근로자 목록 검색/필터 조건(F-03). 쿼리 파라미터를 바인딩한다.
package com.jwgasul.dto;

import com.jwgasul.domain.WorkerType;

public class WorkerFilter {

    private WorkerType type;      // 탭: FOREIGN / KOREAN / null(전체)
    private String q;             // 이름·연락처 검색어
    private String visa;          // 비자 상태: EXPIRED / IMMINENT
    private String edu;           // 교육 상태: EXPIRED / IMMINENT / UNREGISTERED
    private Boolean fixed;        // 고정 인원만
    private Boolean missingDoc;   // 서류 미비(3종 미만)만
    private Boolean noAccount;    // 계좌 미등록만

    // 검색·기본 필터 외에 세부 필터가 하나라도 걸려있는지(패널 기본 열림 판단용)
    public boolean hasDetailFilter() {
        return visa != null || edu != null
                || Boolean.TRUE.equals(fixed)
                || Boolean.TRUE.equals(missingDoc)
                || Boolean.TRUE.equals(noAccount);
    }

    public WorkerType getType() {
        return type;
    }

    public void setType(WorkerType type) {
        this.type = type;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getVisa() {
        return visa;
    }

    public void setVisa(String visa) {
        this.visa = (visa == null || visa.isBlank()) ? null : visa;
    }

    public String getEdu() {
        return edu;
    }

    public void setEdu(String edu) {
        this.edu = (edu == null || edu.isBlank()) ? null : edu;
    }

    public Boolean getFixed() {
        return fixed;
    }

    public void setFixed(Boolean fixed) {
        this.fixed = fixed;
    }

    public Boolean getMissingDoc() {
        return missingDoc;
    }

    public void setMissingDoc(Boolean missingDoc) {
        this.missingDoc = missingDoc;
    }

    public Boolean getNoAccount() {
        return noAccount;
    }

    public void setNoAccount(Boolean noAccount) {
        this.noAccount = noAccount;
    }
}
