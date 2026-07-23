// SiteRepository.java — 현장 조회 리포지토리(3.5)
package com.jwgasul.repository;

import com.jwgasul.domain.Site;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    // 진행 여부 탭별 목록(최근 등록순)
    List<Site> findByActiveOrderByCreatedAtDesc(boolean active);

    // 전체(최근 등록순)
    List<Site> findAllByOrderByCreatedAtDesc();

    // 현장명 중복 검사
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
