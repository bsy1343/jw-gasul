// ActiveSiteView.java — 대시보드의 진행 중 현장 1건(F-11).
// 현장 요약 + 오늘 이후 가장 가까운 명부 날짜를 함께 담아, 어느 현장에 투입 일정이 잡혀 있는지 한눈에 본다.
package com.jwgasul.dto;

import java.time.LocalDate;

public record ActiveSiteView(
        Long id,
        String name,
        LocalDate endDate,
        LocalDate nextRosterDate   // 오늘 포함 이후 첫 명부 날짜. 없으면 null
) {

    // 다음 명부가 오늘인지(강조 표시용)
    public boolean isRosterToday() {
        return nextRosterDate != null && nextRosterDate.isEqual(LocalDate.now());
    }

    // 다음 명부 표시 문구: 오늘 / 내일 / 날짜 / 예정 없음
    public String nextRosterLabel() {
        if (nextRosterDate == null) {
            return "명부 예정 없음";
        }
        LocalDate today = LocalDate.now();
        if (nextRosterDate.isEqual(today)) {
            return "오늘 명부";
        }
        if (nextRosterDate.isEqual(today.plusDays(1))) {
            return "내일 명부";
        }
        return nextRosterDate + " 예정";
    }
}
