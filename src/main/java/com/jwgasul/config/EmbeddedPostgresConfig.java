// EmbeddedPostgresConfig.java — test 프로필 전용 임베디드 PostgreSQL DataSource.
// 외부 PostgreSQL/Docker 없이 실 PG 바이너리를 프로세스로 띄워 애플리케이션을 기동한다(SCAFFOLD: 기본 test 프로필).
// !test(prod) 프로필에서는 이 구성이 비활성화되고 spring.datasource(환경변수)를 사용한다.
package com.jwgasul.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class EmbeddedPostgresConfig {

    // PG 프로세스를 시작한다. 컨텍스트 종료 시 close로 정리한다.
    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    // 임베디드 PG에 연결되는 DataSource. 이 빈이 있으면 Boot의 자동 DataSource 구성은 물러난다.
    // Flyway가 스키마(jwgasul)와 테이블을 생성하고, JPA는 매핑을 검증한다.
    @Bean
    public DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        return embeddedPostgres.getPostgresDatabase();
    }
}
