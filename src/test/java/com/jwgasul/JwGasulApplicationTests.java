// JwGasulApplicationTests.java — 애플리케이션 컨텍스트 로딩 검증(임베디드 PG + Flyway 마이그레이션 + JPA validate)
package com.jwgasul;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class JwGasulApplicationTests {

	// 컨텍스트가 정상 로딩되면(임베디드 PG 기동·스키마 마이그레이션·엔티티 매핑 검증 통과) 성공
	@Test
	void contextLoads() {
	}

}
