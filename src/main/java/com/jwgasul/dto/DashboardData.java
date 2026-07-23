// DashboardData.java — 대시보드 집계 데이터(F-11)
package com.jwgasul.dto;

import com.jwgasul.domain.Roster;
import com.jwgasul.domain.Worker;
import java.util.List;

public record DashboardData(
        long totalWorkers,
        long foreignCount,
        long koreanCount,
        long activeSiteCount,
        ExpirySummary expiry,      // 비자/교육 만료·임박 인원 수
        long missingDocCount,      // 서류 미비
        long noAccountCount,       // 계좌 미등록
        List<Worker> attention,    // 만료·임박 인원 리스트(바로가기)
        List<Roster> recentRosters // 최근 명부 5건
) {
}
