// PhoneFormat.java — 연락처 표시 포맷 공용 유틸(횡단 관심사).
// 저장은 숫자만(WorkerService.normalizePhone), 화면에는 010-1234-5678로 보여준다.
// 근로자(Worker)와 명부 스냅샷(RosterMember) 양쪽에서 같은 규칙을 쓰기 위해 분리했다.
package com.jwgasul.common;

public final class PhoneFormat {

    private PhoneFormat() {
    }

    // 숫자만 남긴 뒤 자릿수에 맞춰 하이픈을 넣는다. 예상 형식이 아니면 원본을 그대로 돌려준다.
    public static String format(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11) {                            // 010-1234-5678
            return digits.replaceFirst("(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3");
        }
        if (digits.length() == 10) {                            // 02-1234-5678 / 031-123-4567
            return digits.startsWith("02")
                    ? digits.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "$1-$2-$3")
                    : digits.replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3");
        }
        if (digits.length() == 9 && digits.startsWith("02")) {   // 02-123-4567
            return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{4})", "$1-$2-$3");
        }
        return phone;
    }
}
