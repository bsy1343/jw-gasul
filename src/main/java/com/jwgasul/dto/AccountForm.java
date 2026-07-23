// AccountForm.java — 계좌 등록/수정 폼 바인딩 DTO(F-09). 계좌번호는 서비스에서 숫자만 정규화.
package com.jwgasul.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AccountForm {

    @NotBlank(message = "은행명은 필수입니다")
    @Size(max = 30)
    private String bankName;

    @NotBlank(message = "계좌번호는 필수입니다")
    @Pattern(regexp = "[0-9-]{6,30}", message = "계좌번호는 숫자와 하이픈만 입력하세요")
    private String accountNumber;

    @NotBlank(message = "예금주는 필수입니다")
    @Size(max = 50)
    private String accountHolder;

    @Size(max = 100)
    private String memo;

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
