// RosterRepository.java — 명부 이력 조회 리포지토리(3.6)
package com.jwgasul.repository;

import com.jwgasul.domain.Roster;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterRepository extends JpaRepository<Roster, Long> {

    // 전체 이력(최근순)
    List<Roster> findAllByOrderByCreatedAtDesc();

    // 특정 현장의 명부 이력(현장 상세용) — 투입 날짜 오름차순(같은 날짜면 생성순)
    List<Roster> findBySiteIdOrderByTargetDateAscCreatedAtAsc(Long siteId);

    // 같은 현장·같은 날짜의 기존 명부(중복 방지). 등록 현장 기준.
    Optional<Roster> findFirstBySiteIdAndTargetDate(Long siteId, LocalDate targetDate);

    // 같은 임시 제목·같은 날짜의 기존 명부(중복 방지). 현장 미선택(직접 입력) 기준.
    Optional<Roster> findFirstBySiteIdIsNullAndTitleAndTargetDate(String title, LocalDate targetDate);

    // 현장 참조 명부 수(현장 삭제 차단 판단, F-05)
    long countBySiteId(Long siteId);

    // 최근 생성 명부 5건(대시보드, F-11)
    List<Roster> findTop5ByOrderByCreatedAtDesc();

    // 해당 현장의 기준일 이후 가장 가까운 명부(대시보드 '다음 명부', F-11)
    Optional<Roster> findFirstBySiteIdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(
            Long siteId, LocalDate from);
}
