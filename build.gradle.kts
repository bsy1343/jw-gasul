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

	// 임베디드 PostgreSQL(Zonky) — 테스트 전용.
	// 운영(prod)은 홈서버 PostgreSQL에 접속하므로 테스트 클래스패스에만 둔다.
	testImplementation("io.zonky.test:embedded-postgres:2.2.2") {
		exclude(group = "io.zonky.test.postgres")
	}

	// PG17 바이너리 — 로컬(macOS arm64) + Docker CI(Linux arm64) 양쪽 지원.
	// Zonky가 실행 OS/아키텍처에 맞는 바이너리를 런타임에 자동 선택.
	testRuntimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.6.0"))
	testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
	testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")

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