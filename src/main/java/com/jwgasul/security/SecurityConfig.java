// SecurityConfig.java — Spring Security 7 폼 로그인(람다 DSL). BCrypt, username 기준 실패 잠금(F-01).
// 주의: Spring Boot 3.x의 .and() 체이닝을 사용하지 않는다(람다 DSL만).
package com.jwgasul.security;

import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginAttemptService loginAttemptService;

    public SecurityConfig(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    // 비밀번호 해시 인코더(BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 보안 필터 체인: 폼 로그인 + 정적/로그인 경로 허용 + 나머지 인증 요구
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler())
                        .failureHandler(failureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }

    // 로그인 성공 시 실패 카운터를 초기화하고 대시보드로 이동한다
    private AuthenticationSuccessHandler successHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler("/");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            loginAttemptService.reset(authentication.getName());
            handler.onAuthenticationSuccess(request, response, authentication);
        };
    }

    // 로그인 실패 시 username 기준 실패를 기록하고, 잠금 여부에 따라 리다이렉트한다
    private AuthenticationFailureHandler failureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            // 잠금 상태에서 재시도한 경우(LockedException)는 카운터를 더 올리지 않는다
            if (!(exception instanceof LockedException)) {
                loginAttemptService.recordFailure(username, Instant.now());
            }
            boolean locked = username != null && loginAttemptService.isLocked(username, Instant.now());
            response.sendRedirect(request.getContextPath() + (locked ? "/login?locked" : "/login?error"));
        };
    }
}
