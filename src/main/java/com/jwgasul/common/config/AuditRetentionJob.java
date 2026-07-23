// AuditRetentionJob.java — 감사 로그 보관 기간(기본 365일) 경과분 자동 정리 배치(F-12).
// test 프로필(임베디드 PG·데이터 리셋)에서는 불필요하여 비활성. 운영(prod 등)에서만 스케줄 동작.
package com.jwgasul.common.config;

import com.jwgasul.service.AuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@Profile("!test")
@EnableScheduling
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditService auditService;
    private final int retentionDays;

    public AuditRetentionJob(AuditService auditService,
                             @Value("${app.audit.retention-days:365}") int retentionDays) {
        this.auditService = auditService;
        this.retentionDays = retentionDays;
    }

    // 매일 새벽 3시 30분(서버 시각) 보관 기간 경과 로그 삭제
    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = auditService.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("감사 로그 보관정리: {}건 삭제(기준 {} 이전)", deleted, cutoff);
        }
    }
}
