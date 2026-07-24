// RosterMemberRepository.java — 명부 구성원 조회 리포지토리(3.6)
package com.jwgasul.repository;

import com.jwgasul.domain.RosterMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterMemberRepository extends JpaRepository<RosterMember, Long> {

    // 명부의 구성원 목록
    List<RosterMember> findByRosterId(Long rosterId);

    long countByRosterId(Long rosterId);

    // 명부 삭제 시 구성원 스냅샷도 함께 제거(DB에도 ON DELETE CASCADE가 있지만 JPA 쪽에서 명시적으로 지운다)
    long deleteByRosterId(Long rosterId);
}
