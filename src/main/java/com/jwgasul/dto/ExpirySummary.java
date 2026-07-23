// ExpirySummary.java — 목록 상단 요약 배지용 만료/임박 인원 집계(F-04)
package com.jwgasul.dto;

public record ExpirySummary(
        long visaExpired, long visaImminent,
        long eduExpired, long eduImminent) {
}
