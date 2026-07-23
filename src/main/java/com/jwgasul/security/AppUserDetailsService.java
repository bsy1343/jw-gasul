// AppUserDetailsService.java — Spring Security 인증용 UserDetailsService 구현.
// app_user 조회 + username 기준 잠금 상태를 UserDetails에 반영한다(F-01).
package com.jwgasul.security;

import com.jwgasul.user.AppUser;
import com.jwgasul.user.AppUserRepository;
import java.time.Instant;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public AppUserDetailsService(AppUserRepository userRepository, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    // 사용자명으로 계정을 조회해 UserDetails로 변환한다. 잠금 상태면 accountLocked=true로 반환.
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + username));

        boolean locked = loginAttemptService.isLocked(username, Instant.now());

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRole())
                .disabled(!user.isEnabled())
                .accountLocked(locked)
                .build();
    }
}
