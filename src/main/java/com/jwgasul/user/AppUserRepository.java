// AppUserRepository.java — app_user 조회 리포지토리
package com.jwgasul.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 로그인 시 사용자명으로 계정을 조회한다
    Optional<AppUser> findByUsername(String username);

    // 계정 존재 여부(seed 초기화 판단)
    boolean existsByUsername(String username);
}
