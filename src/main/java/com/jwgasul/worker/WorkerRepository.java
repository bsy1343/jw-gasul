// WorkerRepository.java — 근로자 조회/검색 리포지토리. soft delete 제외를 기본으로 한다(3.1)
// 목록 검색은 동적 조건(유형·키워드·필터 확장)을 위해 JpaSpecificationExecutor를 사용한다
// (null 파라미터를 DB로 보내지 않아 PostgreSQL 타입 추론 문제를 피한다).
package com.jwgasul.worker;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkerRepository extends JpaRepository<Worker, Long>, JpaSpecificationExecutor<Worker> {

    // 삭제되지 않은 단건 조회
    Optional<Worker> findByIdAndDeletedAtIsNull(Long id);

    // 재등록 중복 검사(삭제되지 않은 동일인) — 부분 유니크와 동일 조건(3.1)
    boolean existsByNameKoAndBirthDateAndPhoneAndDeletedAtIsNull(String nameKo, LocalDate birthDate, String phone);

    // 유형별 인원 수(대시보드/탭 카운트용)
    long countByDeletedAtIsNull();

    long countByWorkerTypeAndDeletedAtIsNull(WorkerType workerType);
}
