// WorkerForm.java — 근로자 등록/수정 폼 바인딩 DTO(F-02). 유형별 표시 규칙은 서비스에서 적용.
package com.jwgasul.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class WorkerForm {

    @NotNull(message = "유형을 선택하세요")
    private WorkerType workerType;

    @NotBlank(message = "한국 이름은 필수입니다")
    @Size(max = 50)
    private String nameKo;

    @Size(max = 100)
    private String nameForeign;

    @NotNull(message = "생년월일은 필수입니다")
    @Past(message = "생년월일은 과거 날짜여야 합니다")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    // 숫자만 저장. 하이픈 포함 입력도 서비스에서 정규화하지만 형식은 최소 검증.
    @NotBlank(message = "휴대폰 번호는 필수입니다")
    @Pattern(regexp = "[0-9-]{9,20}", message = "휴대폰 번호 형식이 올바르지 않습니다")
    private String phone;

    @Size(max = 50)
    private String nationality;

    @Size(max = 20)
    private String visaGrade;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate visaExpireDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate eduCompleteDate;

    private boolean fixed;

    private String memo;

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
}
