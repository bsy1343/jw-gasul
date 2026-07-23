// WorkerRepository.java — 근로자 조회/검색 리포지토리. soft delete 제외를 기본으로 한다(3.1)
// 목록 검색은 동적 조건(유형·키워드·필터 확장)을 위해 JpaSpecificationExecutor를 사용한다
// (null 파라미터를 DB로 보내지 않아 PostgreSQL 타입 추론 문제를 피한다).
package com.jwgasul.repository;

import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerRepository extends JpaRepository<Worker, Long>, JpaSpecificationExecutor<Worker> {

    // 삭제되지 않은 단건 조회
    Optional<Worker> findByIdAndDeletedAtIsNull(Long id);

    // 재등록 중복 검사(삭제되지 않은 동일인) — 부분 유니크와 동일 조건(3.1)
    boolean existsByNameKoAndBirthDateAndPhoneAndDeletedAtIsNull(String nameKo, LocalDate birthDate, String phone);

    // 유형별 인원 수(대시보드/탭 카운트용)
    long countByDeletedAtIsNull();

    long countByWorkerTypeAndDeletedAtIsNull(WorkerType workerType);

    // --- 만료/임박 집계(F-04). 9999-12-31은 today보다 항상 크므로 자동 제외. edu null도 자동 제외. ---
    @Query("select count(w) from Worker w where w.deletedAt is null and w.visaExpireDate < :today")
    long countVisaExpired(@Param("today") LocalDate today);

    @Query("select count(w) from Worker w where w.deletedAt is null and w.visaExpireDate between :today and :limit")
    long countVisaImminent(@Param("today") LocalDate today, @Param("limit") LocalDate limit);

    @Query("select count(w) from Worker w where w.deletedAt is null and w.eduExpireDate < :today")
    long countEduExpired(@Param("today") LocalDate today);

    @Query("select count(w) from Worker w where w.deletedAt is null and w.eduExpireDate between :today and :limit")
    long countEduImminent(@Param("today") LocalDate today, @Param("limit") LocalDate limit);

    // --- 대시보드(F-11) ---
    // 서류 미비(3종 미만) 인원 수
    @Query("select count(w) from Worker w where w.deletedAt is null "
            + "and (select count(d) from WorkerDocument d where d.workerId = w.id) < 3")
    long countMissingDoc();

    // 계좌 미등록 인원 수
    @Query("select count(w) from Worker w where w.deletedAt is null "
            + "and (select count(a) from WorkerAccount a where a.workerId = w.id) = 0")
    long countNoAccount();

    // 비자/교육이 만료·임박(<= limit)인 주의 인원(빠른 만료순). limit=today+7.
    @Query("select w from Worker w where w.deletedAt is null "
            + "and (w.visaExpireDate <= :limit or (w.eduExpireDate is not null and w.eduExpireDate <= :limit)) "
            + "order by w.visaExpireDate asc")
    List<Worker> findAttentionNeeded(@Param("limit") LocalDate limit, Pageable pageable);
}
