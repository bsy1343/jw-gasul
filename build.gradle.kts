// build.gradle.kts — 빌드 스크립트: Spring Boot 4.1 / JDK 21 / 의존성 및 테스트 설정
plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.jwgasul"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	// Apache POI — 엑셀 명부 다운로드(F-08). Spring Boot BOM이 관리하지 않으므로 버전 명시
	implementation("org.apache.poi:poi-ooxml:5.3.0")
	runtimeOnly("org.postgresql:postgresql")

	// 임베디드 PostgreSQL(Zonky) — test 프로필에서 외부 서버 없이 실 PG를 프로세스로 실행.
	// EmbeddedPostgresConfig(src/main, @Profile("test"))가 이 클래스를 쓰므로 main 클래스패스에 둔다.
	// bootRun(기본 test 프로필)·현재 배포(test 프로필)·테스트가 모두 외부 DB 없이 이 구성으로 기동한다.
	// ※ 운영을 prod+실 PostgreSQL로 전환하면 그때 EmbeddedPostgresConfig를 src/test로 옮기고
	//    아래를 testImplementation/testRuntimeOnly로 내려 런타임 이미지에서 제거할 수 있다.
	implementation("io.zonky.test:embedded-postgres:2.2.2") {
		exclude(group = "io.zonky.test.postgres")
	}
	runtimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.6.0"))
	runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
	runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// ArchUnit — 레이어(Controller→Service→Repository) 의존성 규칙을 CI에서 강제
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }