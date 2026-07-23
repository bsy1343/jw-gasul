// LoginAttemptService.java — username 기준 로그인 실패 잠금(F-01). 인메모리 카운터.
// 사용자 1~3명 소규모 단일 인스턴스 전제이므로 인메모리로 충분하다.
// (분산/영속 카운터가 필요해지면 저장소를 교체한다 — PRD "남은 구현 단계 세부 결정")
package com.jwgasul.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    // 실패 이력: username -> (연속 실패 횟수, 잠금 해제 시각)
    private record Attempt(int count, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.lock-minutes:10}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    // 로그인 실패를 기록하고, 임계 도달 시 잠금 시각을 설정한다
    public void recordFailure(String username, Instant now) {
        if (username == null || username.isBlank()) {
            return;
        }
        attempts.compute(username, (key, prev) -> {
            int nextCount = (prev == null ? 0 : prev.count()) + 1;
            Instant lockedUntil = nextCount >= maxAttempts ? now.plus(lockDuration) : null;
            return new Attempt(nextCount, lockedUntil);
        });
    }

    // 로그인 성공 시 카운터를 초기화한다
    public void reset(String username) {
        if (username != null) {
            attempts.remove(username);
        }
    }

    // 현재 잠금 상태인지 확인한다(잠금 시간이 지나면 자동 해제)
    public boolean isLocked(String username, Instant now) {
        Attempt attempt = attempts.get(username);
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (now.isAfter(attempt.lockedUntil())) {
            attempts.remove(username);
            return false;
        }
        return true;
    }
}
