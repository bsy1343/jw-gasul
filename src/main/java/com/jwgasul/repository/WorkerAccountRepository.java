// WorkerAccountRepository.java — 근로자 계좌 조회 리포지토리(3.3)
package com.jwgasul.repository;

import com.jwgasul.domain.WorkerAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerAccountRepository extends JpaRepository<WorkerAccount, Long> {

    // 근로자의 계좌 목록(표시 순서대로)
    List<WorkerAccount> findByWorkerIdOrderBySortOrderAsc(Long workerId);

    // 근로자 계좌 수(최대 3개 제한 검사)
    long countByWorkerId(Long workerId);

    // 주계좌(모드 C 엑셀·기본 표시용)
    Optional<WorkerAccount> findByWorkerIdAndPrimaryTrue(Long workerId);
}
