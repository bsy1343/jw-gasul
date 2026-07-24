// DashboardService.java — 대시보드 집계(F-11). 여러 도메인의 요약을 한 번에 조립한다.
package com.jwgasul.service;

import com.jwgasul.domain.ExpiryStatus;
import com.jwgasul.domain.Roster;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.ActiveSiteView;
import com.jwgasul.dto.DashboardData;
import com.jwgasul.repository.RosterRepository;
import com.jwgasul.repository.SiteRepository;
import com.jwgasul.repository.WorkerRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final WorkerRepository workerRepository;
    private final SiteRepository siteRepository;
    private final RosterRepository rosterRepository;
    private final WorkerService workerService;

    public DashboardService(WorkerRepository workerRepository, SiteRepository siteRepository,
                            RosterRepository rosterRepository, WorkerService workerService) {
        this.workerRepository = workerRepository;
        this.siteRepository = siteRepository;
        this.rosterRepository = rosterRepository;
        this.workerService = workerService;
    }

    // 대시보드 데이터 조립
    @Transactional(readOnly = true)
    public DashboardData load() {
        LocalDate limit = LocalDate.now().plusDays(ExpiryStatus.IMMINENT_DAYS);
        return new DashboardData(
                workerRepository.countByDeletedAtIsNull(),
                workerRepository.countByWorkerTypeAndDeletedAtIsNull(WorkerType.FOREIGN),
                workerRepository.countByWorkerTypeAndDeletedAtIsNull(WorkerType.KOREAN),
                siteRepository.countByActiveTrue(),
                workerService.expirySummary(),
                workerRepository.countMissingDoc(),
                workerRepository.countNoAccount(),
                workerRepository.findAttentionNeeded(limit, PageRequest.of(0, 20)),
                rosterRepository.findTop5ByOrderByCreatedAtDesc(),
                activeSites());
    }

    // 진행 중 현장 + 각 현장의 다음 명부 일정(최대 5건).
    // 명부가 임박한 현장을 위로, 예정 없는 현장을 아래로 정렬한다.
    // 현장 건당 명부 1건씩 조회하지만 진행 현장이 소수라 비용이 미미하다(소규모 전제).
    private List<ActiveSiteView> activeSites() {
        LocalDate today = LocalDate.now();
        return siteRepository.findByActiveOrderByCreatedAtDesc(true).stream()
                .map(site -> new ActiveSiteView(
                        site.getId(),
                        site.getName(),
                        site.getEndDate(),
                        rosterRepository
                                .findFirstBySiteIdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(site.getId(), today)
                                .map(Roster::getTargetDate)
                                .orElse(null)))
                .sorted(Comparator.comparing(ActiveSiteView::nextRosterDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .toList();
    }
}
