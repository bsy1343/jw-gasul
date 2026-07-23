// ExpiryStatus.java — 비자/교육 유효기간 상태(F-04). 화면 배지·집계·명부 제외 판정에 사용.
package com.jwgasul.domain;

import java.time.LocalDate;

public enum ExpiryStatus {
    NONE,          // 해당없음 (visa 9999-12-31) — 표시 '-', 집계 제외
    UNREGISTERED,  // 미등록 (만료일 null, 교육만 해당)
    EXPIRED,       // 만료 (expire < today)
    IMMINENT,      // 임박 (today ≤ expire ≤ today+7)
    NORMAL;        // 정상 (expire > today+7)

    // 임박 판정 기준일 수(D-7)
    public static final int IMMINENT_DAYS = 7;

    // 만료일과 기준일(today)로 상태를 판정한다. visaNone=true면 관례값(9999) → NONE.
    public static ExpiryStatus of(LocalDate expire, boolean visaNone, LocalDate today) {
        if (visaNone) {
            return NONE;
        }
        if (expire == null) {
            return UNREGISTERED;
        }
        if (expire.isBefore(today)) {
            return EXPIRED;
        }
        if (!expire.isAfter(today.plusDays(IMMINENT_DAYS))) {
            return IMMINENT;
        }
        return NORMAL;
    }
}
