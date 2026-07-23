// AccountView.java — 계좌 표시용 DTO(F-09). 목록·상세엔 항상 마스킹된 번호만 싣는다(전체는 별도 조회+감사).
package com.jwgasul.dto;

public record AccountView(
        Long id,
        String bankName,
        String maskedNumber,   // 예: 1234-****-5678 (전체 번호는 담지 않음)
        String accountHolder,
        boolean primary,
        String memo) {

    // 계좌번호(숫자만) 마스킹: 앞 4 + 뒤 4만 노출, 중간 마스킹. 짧으면 뒤 2만 노출.
    public static String mask(String digits) {
        if (digits == null || digits.isBlank()) {
            return "";
        }
        String d = digits.trim();
        if (d.length() <= 4) {
            return "*".repeat(d.length());
        }
        if (d.length() <= 8) {
            String last2 = d.substring(d.length() - 2);
            return "*".repeat(d.length() - 2) + last2;
        }
        String first4 = d.substring(0, 4);
        String last4 = d.substring(d.length() - 4);
        return first4 + "-" + "*".repeat(d.length() - 8) + "-" + last4;
    }
}
