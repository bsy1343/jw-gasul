# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

> 요구사항·설계의 근거는 `docs/PRD.md`에 있다. 이 문서는 **어떻게 구현하는가**(빌드·패턴·컨벤션·함정)만 다룬다. PRD와 내용을 중복하지 않는다.

## Project Overview

**jw-gasul (재원가설 인원관리)** — 비계 시공 근로자의 인적사항·신분증·계좌·비자/교육 유효기간을 관리하고 현장 투입 명부를 생성·엑셀 출력하는 사내 웹앱. 사용자 1~3명, 단일 권한(역할 분리 없음).

- **스택**: Spring Boot 4.1 (Spring Framework 7) / JDK 21 / Thymeleaf + HTMX / Tailwind 4 + DaisyUI 5 / PostgreSQL + Flyway + Spring Data JPA / Spring Security 7 / Apache POI
- **구조**: SSR 단일 모듈(Thymeleaf). 외부 API 연동 없음 → 서비스 인터페이스/구현 분리 규칙 미적용(단, 파일 저장은 `StorageService`로 추상화 예정).
- **배포**: Mac Mini M4 홈서버(OrbStack/Docker) + Cloudflare Tunnel + Nginx Proxy Manager.

## Build and Run Commands

```bash
# 실행 (기본 test 프로필 = 임베디드 PostgreSQL, 외부 서버/Docker 불필요)
./gradlew bootRun
#  → http://localhost:8080  로그인: admin / admin  (개발용 seed 계정)

# 빌드 + 전체 테스트 (임베디드 PG로 마이그레이션·매핑·로그인 플로우까지 검증, Docker 불필요)
./gradlew build
./gradlew test

# 단일 테스트
./gradlew test --tests "com.jwgasul.LoginFlowTest"

# 운영(prod) 프로필로 실행 — 홈서버 실 PostgreSQL, 접속 정보는 환경변수 주입
SPRING_PROFILES_ACTIVE=prod \
  DB_URL=jdbc:postgresql://<host>:5432/<db> DB_USERNAME=... DB_PASSWORD=... \
  SEED_ADMIN_PASSWORD=... \
  ./gradlew bootRun

# 로컬에서 "실 PostgreSQL"로 돌려보고 싶을 때(선택) — compose로 PG 기동 후 prod 프로필
docker compose up -d
```

- **첫 `bootRun`/`test`는 Zonky가 PostgreSQL 바이너리를 내려받아 느릴 수 있다**(이후 캐시).
- 빌드 머신에 JDK 21이 없으면 Gradle 툴체인(foojay)이 자동 프로비저닝한다(현 개발 환경은 JDK 23 설치).

## Architecture

### 프로필 기반 실행 (test / prod)

- **`test`(기본)**: `EmbeddedPostgresConfig`(`@Profile("test")`)가 프로세스 내 실 PostgreSQL을 띄워 `DataSource` 빈을 제공한다 → 외부 서버 없이 즉시 실행. DataSource 빈이 있으면 Boot의 자동 DataSource 구성은 물러난다.
- **`prod`(`!test`)**: 임베디드 구성 비활성. `spring.datasource.*`(환경변수)로 홈서버 PostgreSQL에 접속.
- 마이그레이션은 **PG 전용 한 벌**(`db/migration/V1__init.sql`)로 test·prod 동일하게 검증된다. H2를 쓰지 않는 이유: 부분 유니크 인덱스(`WHERE`)·`TIMESTAMPTZ` 등 PG 문법 때문.

### 스키마 소유권

- **Flyway가 스키마(`jwgasul`)와 테이블을 소유**한다. 스키마 변경은 반드시 새 `V{n}__*.sql` 마이그레이션으로 한다(기존 파일 수정 금지).
- JPA는 `ddl-auto: validate` — 엔티티↔테이블 매핑 불일치를 기동 시 잡는다. 엔티티를 바꾸면 대응 마이그레이션을 같이 추가한다.

### 패키지 구조

```
com.jwgasul
├── config/     설정·초기화 (EmbeddedPostgresConfig, DataInitializer)
├── security/   인증 (SecurityConfig, AppUserDetailsService, LoginAttemptService)
├── user/       계정 도메인 (AppUser, AppUserRepository)
└── web/        컨트롤러 (LoginController, HomeController)
```
- 새 도메인은 도메인별 패키지(예: `worker/`, `site/`, `roster/`, `account/`, `audit/`)로 추가하고, 각 패키지 안에 엔티티·리포지토리·서비스·컨트롤러를 둔다.
- DB CRUD·유틸리티 같은 순수 내부 로직은 인터페이스/구현 분리하지 않는다(SCAFFOLD 기준).

### 인증 (Spring Security 7)

- 폼 로그인 + 세션. `SecurityConfig`는 **람다 DSL만** 사용한다(3.x `.and()` 금지).
- 로그인 실패 잠금은 **username 기준**(`LoginAttemptService`, 인메모리) — IP 기준 아님(Cloudflare Tunnel 뒤에서 전원 잠김 방지). `AppUserDetailsService`가 잠금 상태를 `accountLocked`로 반영.
- 실제 클라이언트 IP는 `CF-Connecting-IP` 헤더로 복원(`server.forward-headers-strategy: framework`).

## Key Configuration

- **설정 파일**: `src/main/resources/application.yml`. 기본 문서 = 공통/test, `spring.config.activate.on-profile: prod` 문서 = 운영 오버라이드.
- **커스텀 설정**은 `app.*` 아래에 둔다(PRD 6.5). 정책성 값(교육 유효기간, 명부 상한, 로그인 임계)은 코드 하드코딩하지 말고 `application.yml` + 환경변수로 외부화한다.
  - `app.edu.valid-years.{foreign|korean}`(기본 2), `app.roster.max-members`(100), `app.security.login.{max-attempts,lock-minutes}`, `app.edu.ent-code`, `app.upload.dir`
- **DB 연결은 읽기전용이 기본**이라는 공통 규칙이 있으나, 본 앱은 CRUD가 본체라 **읽기·쓰기 연결**을 사용한다(PRD에 명시된 예외). 향후 별도 읽기전용 리포팅이 생기면 그때 분리한다.
- **seed 계정**: `DataInitializer`가 기동 시 `app.seed.admin.*`로 관리자 1건을 생성(없을 때만). 운영은 `SEED_ADMIN_PASSWORD` 등 환경변수로 반드시 교체.

## API Endpoints / Entry Points

- 라우팅은 화면 단위(PRD 5장): `/login`, `/`(대시보드), `/workers`, `/workers/new`, `/workers/{id}`, `/sites`, `/roster/random`, `/roster/selected`, `/roster`, `/audit`, `/files/{path}`(인증 필수 이미지 서빙).
- 정적/로그인 경로(`/login`, `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`)만 `permitAll`, 나머지는 인증 요구(`SecurityConfig`).

## Important Notes

- **모든 파일 최상단에 파일 역할을 주석으로**, **모든 함수 바로 위에 기능 설명을 주석으로** 기술한다. 주석 언어는 **한국어**.
- **Spring Boot 4.1 / Framework 7 / Security 7 기준으로 작성. 3.x 문법 금지.** 코드 생성 시 프롬프트에 명시할 것(학습 데이터에 3.x가 많음).
  - Security: **람다 DSL만**(`.and()` 없음).
  - 스타터 이름이 3.x와 다름: `spring-boot-starter-webmvc`(≠`-web`), `spring-boot-starter-flyway`, 모듈별 `-test` 스타터.
  - **test-autoconfigure 패키지 이동**: `@AutoConfigureMockMvc` = `org.springframework.boot.webmvc.test.autoconfigure.*` (구 `...boot.test.autoconfigure.web.servlet.*` 아님).
- **Security 설정 클래스(`SecurityConfig`)는 변경 시 사람이 검토**한다(PRD 지침).
- **Testcontainers 대신 임베디드 PG(Zonky)** 를 쓴다 — 테스트도 Docker 불필요. PG 바이너리는 `runtimeOnly`로 두어 **모든 실행 방식(`./gradlew bootRun`·IDE의 main() 실행·테스트)** 에서 잡히게 한다(`developmentOnly`는 IDE main() 실행에서 누락되어 임베디드 PG가 기동 실패함). prod 프로필에선 미로드라 운영 무영향. 현재 `darwin-arm64v8`만 포함(타 플랫폼 CI 추가 시 해당 바이너리 의존성 추가).
- **Apache POI 버전은 수동 관리**(Boot BOM 미관리). `poi-ooxml:5.3.0`.
- 계좌번호·신분증은 민감정보다. 화면 마스킹 + 접근 감사 로그(F-12)를 우회하는 코드를 만들지 않는다. 계좌 전체 노출·계좌 포함 엑셀 반출은 반드시 `audit_log`에 남긴다.

## Operational Decisions

- **임베디드 PG를 기본 프로필로 채택한 이유**: 사용자가 개발/실행 시 Docker·외부 PostgreSQL을 준비하지 않아도 되게 하기 위함. 실 PG 바이너리라 마이그레이션 충실도를 유지한다.
- **감사 로그는 서비스 레이어 명시적 diff로 구현**(Envers·`@EntityListeners` 미채택) — 계좌 조회(`VIEW`)·엑셀 반출 같은 비-CRUD 이벤트를 CRUD와 같은 경로로 남기고 수행자·실 IP를 명확히 주입하기 위함(PRD F-12).
- **미완료(향후 단계)**: 근로자/현장/명부/계좌/서류/대시보드/감사로그 화면·로직(PRD 개발 단계 2~10), 파일 저장 `StorageService` 추상화(Stage 2), Tailwind+DaisyUI 정식 빌드(현재 로그인/홈은 Tailwind 브라우저 CDN 임시 사용), 로그인 실패 잠금 지속시간·저장소 확정.
