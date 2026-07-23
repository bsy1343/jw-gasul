// DataInitializer.java — 초기 관리자 계정 seed(3.4). 계정이 없을 때만 설정값으로 1건 생성한다.
// 운영에서는 app.seed.* 를 환경변수로 주입해 비밀번호를 교체한다.
package com.jwgasul.config;

import com.jwgasul.user.AppUser;
import com.jwgasul.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedUsername;
    private final String seedPassword;
    private final String seedDisplayName;

    public DataInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin.username:admin}") String seedUsername,
            @Value("${app.seed.admin.password:changeme123!}") String seedPassword,
            @Value("${app.seed.admin.display-name:관리자}") String seedDisplayName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
        this.seedDisplayName = seedDisplayName;
    }

    // 애플리케이션 기동 시 seed 관리자 계정이 없으면 생성한다
    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(seedUsername)) {
            return;
        }
        AppUser admin = new AppUser(
                seedUsername,
                passwordEncoder.encode(seedPassword),
                seedDisplayName,
                "ROLE_ADMIN",
                true);
        userRepository.save(admin);
        log.info("Seed 관리자 계정 생성: username={}", seedUsername);
    }
}
