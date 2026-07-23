// RosterMemberRepository.java — 명부 구성원 조회 리포지토리(3.6)
package com.jwgasul.repository;

import com.jwgasul.domain.RosterMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterMemberRepository extends JpaRepository<RosterMember, Long> {

    // 명부의 구성원 목록
    List<RosterMember> findByRosterId(Long rosterId);

    long countByRosterId(Long rosterId);
}
