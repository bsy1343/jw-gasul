// RosterService.java — 명부 생성(랜덤/수동)·저장(인적사항 스냅샷)·이력 조회(F-06·F-07, 3.6).
// 후보 풀 필터/제외는 소규모 인원 전제로 인메모리 처리한다.
package com.jwgasul.service;

import com.jwgasul.domain.ExpiryStatus;
import com.jwgasul.domain.Roster;
import com.jwgasul.domain.RosterMember;
import com.jwgasul.domain.RosterType;
import com.jwgasul.domain.Site;
import com.jwgasul.domain.Worker;
import com.jwgasul.dto.RandomResult;
import com.jwgasul.dto.RosterCriteria;
import com.jwgasul.repository.RosterMemberRepository;
import com.jwgasul.repository.RosterRepository;
import com.jwgasul.repository.SiteRepository;
import com.jwgasul.repository.WorkerDocumentRepository;
import com.jwgasul.repository.WorkerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RosterService {

    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final RosterRepository rosterRepository;
    private final RosterMemberRepository rosterMemberRepository;
    private final SiteRepository siteRepository;
    private final int maxMembers;

    public RosterService(
            WorkerRepository workerRepository,
            WorkerDocumentRepository documentRepository,
            RosterRepository rosterRepository,
            RosterMemberRepository rosterMemberRepository,
            SiteRepository siteRepository,
            @Value("${app.roster.max-members:100}") int maxMembers) {
        this.workerRepository = workerRepository;
        this.documentRepository = documentRepository;
        this.rosterRepository = rosterRepository;
        this.rosterMemberRepository = rosterMemberRepository;
        this.siteRepository = siteRepository;
        this.maxMembers = maxMembers;
    }

    // 조건에 맞는 후보 풀(삭제 안 됨 + 유형 + 제외 옵션 통과)
    @Transactional(readOnly = true)
    public List<Worker> candidatePool(RosterCriteria c) {
        Specification<Worker> spec = (r, q, cb) -> cb.isNull(r.get("deletedAt"));
        if (c.getType() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("workerType"), c.getType()));
        }
        List<Worker> all = workerRepository.findAll(spec);
        List<Worker> pool = new ArrayList<>();
        for (Worker w : all) {
            if (c.isExcludeVisaExpired() && w.getVisaStatus() == ExpiryStatus.EXPIRED) {
                continue;
            }
            if (c.isExcludeVisaImminent() && w.getVisaStatus() == ExpiryStatus.IMMINENT) {
                continue;
            }
            if (c.isExcludeEduExpired() && w.getEduStatus() == ExpiryStatus.EXPIRED) {
                continue;
            }
            if (c.isExcludeMissingDoc() && documentRepository.findByWorkerId(w.getId()).size() < 3) {
                continue;
            }
            pool.add(w);
        }
        return pool;
    }

    // 랜덤 명부 생성. 고정 인원 항상 포함(N에 포함), 부족하면 가능한 만큼 + 경고(F-06).
    @Transactional(readOnly = true)
    public RandomResult generateRandom(RosterCriteria c) {
        List<Worker> pool = candidatePool(c);
        List<Worker> fixed = new ArrayList<>();
        List<Worker> nonFixed = new ArrayList<>();
        for (Worker w : pool) {
            (w.isFixed() ? fixed : nonFixed).add(w);
        }

        List<Worker> selected = new ArrayList<>(fixed);
        String warning = null;

        if (fixed.size() >= c.getCount()) {
            if (fixed.size() > c.getCount()) {
                warning = "고정 인원(" + fixed.size() + "명)이 요청 인원(" + c.getCount() + "명)보다 많아 고정 인원 전원을 포함했습니다.";
            }
        } else {
            int need = c.getCount() - fixed.size();
            Collections.shuffle(nonFixed);
            List<Worker> picked = nonFixed.subList(0, Math.min(need, nonFixed.size()));
            selected.addAll(picked);
            if (picked.size() < need) {
                warning = "요청 " + c.getCount() + "명 중 " + selected.size() + "명만 가능합니다(필터·제외로 대상이 부족).";
            }
        }
        return new RandomResult(selected, warning);
    }

    // 교체(다시 뽑기): 현재 선택에 없는 비고정 후보 1명(잔여 풀에서). 없으면 empty.
    @Transactional(readOnly = true)
    public Optional<Worker> replaceCandidate(RosterCriteria c, Set<Long> currentIds) {
        List<Worker> remain = new ArrayList<>();
        for (Worker w : candidatePool(c)) {
            if (!w.isFixed() && !currentIds.contains(w.getId())) {
                remain.add(w);
            }
        }
        if (remain.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(remain);
        return Optional.of(remain.get(0));
    }

    // 명부 저장(인적사항 스냅샷). 현장 선택 시 title은 현장명으로, 아니면 입력 제목.
    @Transactional
    public Roster save(RosterType type, RosterCriteria c, List<Long> workerIds, String createdBy) {
        if (workerIds == null || workerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "명부에 포함할 인원이 없습니다");
        }
        if (workerIds.size() > maxMembers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "한 명부 최대 " + maxMembers + "명까지입니다. 나눠서 생성하세요.");
        }
        String title = resolveTitle(c);
        Roster roster = rosterRepository.save(new Roster(c.getSiteId(), title, c.getTargetDate(), type, createdBy));
        for (Long wid : workerIds) {
            Worker w = workerRepository.findById(wid)
                    .orElseThrow(() -> new IllegalArgumentException("근로자를 찾을 수 없습니다: " + wid));
            rosterMemberRepository.save(new RosterMember(
                    roster.getId(), w.getId(), w.getNameKo(), w.getNameForeign(), w.getPhone(), w.getBirthDate()));
        }
        return roster;
    }

    // 현장 선택이면 현장명, 아니면 입력 제목. 둘 다 없으면 예외.
    private String resolveTitle(RosterCriteria c) {
        if (c.getSiteId() != null) {
            Site site = siteRepository.findById(c.getSiteId())
                    .orElseThrow(() -> new IllegalArgumentException("현장을 찾을 수 없습니다: " + c.getSiteId()));
            return site.getName();
        }
        if (StringUtils.hasText(c.getTitle())) {
            return c.getTitle().trim();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현장을 선택하거나 제목을 입력하세요");
    }

    @Transactional(readOnly = true)
    public List<Roster> history() {
        return rosterRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Roster> historyForSite(Long siteId) {
        return rosterRepository.findBySiteIdOrderByCreatedAtDesc(siteId);
    }

    @Transactional(readOnly = true)
    public Roster get(Long id) {
        return rosterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("명부를 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public List<RosterMember> members(Long rosterId) {
        return rosterMemberRepository.findByRosterId(rosterId);
    }
}
