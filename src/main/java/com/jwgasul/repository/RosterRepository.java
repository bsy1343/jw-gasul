// RosterRepository.java — 명부 이력 조회 리포지토리(3.6)
package com.jwgasul.repository;

import com.jwgasul.domain.Roster;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterRepository extends JpaRepository<Roster, Long> {

    // 전체 이력(최근순)
    List<Roster> findAllByOrderByCreatedAtDesc();

    // 특정 현장의 명부 이력(현장 상세용)
    List<Roster> findBySiteIdOrderByCreatedAtDesc(Long siteId);

    // 현장 참조 명부 수(현장 삭제 차단 판단, F-05)
    long countBySiteId(Long siteId);

    // 최근 생성 명부 5건(대시보드, F-11)
    List<Roster> findTop5ByOrderByCreatedAtDesc();
}
