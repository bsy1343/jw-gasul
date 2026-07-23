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
	// core는 실행 클래스패스에 두고, PG 바이너리는 runtimeOnly로 둔다.
	// runtimeOnly로 두는 이유: ./gradlew bootRun·IDE의 main() 실행·테스트 등 "모든 실행 방식"의
	// 런타임 클래스패스에 바이너리가 잡히게 하기 위함(developmentOnly는 IDE main() 실행에서 누락됨).
	// prod 프로필에서는 이 구성이 비활성이라 바이너리가 로드되지 않는다(운영 무영향).
	implementation("io.zonky.test:embedded-postgres:2.2.2") {
		exclude(group = "io.zonky.test.postgres") // 바이너리 버전은 아래에서 명시 관리
	}
	// PG17 바이너리. 로컬 개발(macOS arm64) + Docker CI(리눅스 arm64, OrbStack) 양쪽을 지원한다.
	// Zonky가 실행 OS/아키텍처에 맞는 바이너리를 런타임에 자동 선택한다.
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

tasks.withType<Test> {
	useJUnitPlatform()
}

// 실행 가능한 bootJar만 생성한다(플레인 -plain.jar 비활성). Docker 런타임 COPY 대상을 명확히 한다.
tasks.named<Jar>("jar") {
	enabled = false
}

// CSS는 브라우저 CDN(@tailwindcss/browser@4)으로 로드한다(layout.html) — 빌드 단계에서 Tailwind CLI를
// 내려받지 않아 Docker 콜드 빌드가 가볍고 빠르다. 사용자 1~3명 사내앱이라 CDN 의존이 허용된다.
