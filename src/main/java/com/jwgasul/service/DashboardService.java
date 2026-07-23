// DashboardService.java — 대시보드 집계(F-11). 여러 도메인의 요약을 한 번에 조립한다.
package com.jwgasul.service;

import com.jwgasul.domain.ExpiryStatus;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.DashboardData;
import com.jwgasul.repository.RosterRepository;
import com.jwgasul.repository.SiteRepository;
import com.jwgasul.repository.WorkerRepository;
import java.time.LocalDate;
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
                rosterRepository.findTop5ByOrderByCreatedAtDesc());
    }
}
