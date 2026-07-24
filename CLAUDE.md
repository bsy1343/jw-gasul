# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

> 요구사항·설계의 근거는 `docs/PRD.md`에 있다. 이 문서는 **어떻게 구현하는가**(빌드·패턴·컨벤션·함정)만 다룬다. PRD와 내용을 중복하지 않는다.

## Project Overview

**jw-gasul (재원가설 인원관리)** — 비계 시공 근로자의 인적사항·신분증·계좌·비자/교육 유효기간을 관리하고 현장 투입 명부를 생성·엑셀 출력하는 사내 웹앱. 사용자 1~3명, 단일 권한(역할 분리 없음).

- **스택**: Spring Boot 4.1 (Spring Framework 7) / JDK 21 / Thymeleaf + HTMX / Tailwind 4 / PostgreSQL + Flyway + Spring Data JPA / Spring Security 7 / Apache POI
- **구조**: SSR 단일 모듈(Thymeleaf). **외부 시스템 연동 0개** → Stub/Default·Factory·Adapter 패턴 미적용. 모든 서비스는 단일 concrete 클래스.
- **배포**: Mac Mini M4 홈서버(OrbStack/Docker) + Cloudflare Tunnel + Nginx Proxy Manager. GitHub Actions self-hosted 러너(`mac-mini`).

## Build and Run Commands

```bash
# 실행 (기본 test 프로필 = 임베디드 PostgreSQL + 샘플데이터, 외부 서버/Docker 불필요)
./gradlew bootRun
#  → http://localhost:8080  로그인: admin / admin  (개발용 seed 계정)

# 빌드 + 전체 테스트 (임베디드 PG로 마이그레이션·매핑·로그인·근로자·ArchUnit 검증, Docker 불필요)
./gradlew build
./gradlew test
./gradlew test --tests "com.jwgasul.WorkerFlowTest"

# 운영(prod) 프로필 — 홈서버 실 PostgreSQL, 접속 정보는 환경변수 주입
SPRING_PROFILES_ACTIVE=prod \
  DB_URL=jdbc:postgresql://<host>:5432/<db> DB_USERNAME=... DB_PASSWORD=... \
  SEED_ADMIN_PASSWORD=... ./gradlew bootRun

# Docker(러너와 동일) — CI는 이미지 안에서 테스트, Deploy는 런타임 이미지
docker build --target ci .      # 빌드 + 테스트
docker build -t jw-gasul .      # 런타임 이미지
```

- **첫 `bootRun`/`test`는 Zonky가 PostgreSQL 바이너리를 내려받아 느릴 수 있다**(이후 캐시).
- 빌드 머신에 JDK 21이 없으면 Gradle 툴체인(foojay)이 자동 프로비저닝(현 개발 환경은 JDK 23).

## Architecture

### 패키지 구조 (package-by-layer)

```
com.jwgasul
├── controller/   HTTP 진입점 (LoginController · HomeController · WorkerController · FileController)
├── service/      비즈니스 로직 (WorkerService · StorageService) — 단일 concrete @Service 클래스
├── repository/   Spring Data JPA (AppUserRepository · WorkerRepository · WorkerDocumentRepository)
├── domain/       엔티티·enum (AppUser · Worker · WorkerDocument · WorkerType · DocType) — suffix 없음
├── dto/          폼/요청 DTO (WorkerForm)
├── security/     인증 횡단 관심사 (SecurityConfig · AppUserDetailsService · LoginAttemptService)
└── common/       config/(EmbeddedPostgresConfig · DataInitializer · SampleDataInitializer) · exception/
```

### 레이어 규약 (★ 새 코드는 반드시 이 형식)

- **컨트롤러·서비스는 메뉴(도메인)당 하나. 쪼개지 않는다.** 예: 근로자 메뉴 = `WorkerController` 1 + `WorkerService` 1. 서류 사진 같은 하위 기능도 별도 서비스로 안 나누고 `WorkerService`에 통합한다.
- **리포지토리는 테이블(엔티티)당 하나** (Spring Data JPA 규칙: 1 리포 = 1 엔티티). 한 메뉴가 여러 테이블을 쓰면 리포는 여러 개가 정상(예: `worker` + `worker_document`).
- **인터페이스/impl 분리하지 않는다.** 외부 시스템이 없어 Stub이 불필요하고 구현이 하나뿐 → 단일 concrete 클래스. (나중에 진짜 외부 시스템이 생기면 **그 서비스만** 인터페이스 + Stub(`@Profile("test")`)/Default(`@Profile("!test")`)로 분리)
- **단방향 의존**: `Controller → Service → Repository`. 컨트롤러가 리포지토리 직접 접근 금지. `security/`·`common/`은 횡단 관심사(레이어 검증 제외).
- **ArchUnit(`LayerDependencyTest`)이 CI에서 이 규칙을 강제**한다 — 위반 시 테스트 실패.

### 프로필 기반 실행 (test / prod)

- **`test`(기본)**: `EmbeddedPostgresConfig`(`@Profile("test")`)가 프로세스 내 실 PostgreSQL을 띄워 `DataSource`를 제공 + `SampleDataInitializer`가 샘플 근로자를 시딩 → **외부 서버 없이 즉시 실행. 이 embedded PG + 샘플데이터가 곧 "stub 데이터" 역할**(별도 인메모리 Stub 서비스를 만들지 않는 이유).
- **`prod`(`!test`)**: 임베디드 구성 비활성. `spring.datasource.*`(환경변수)로 홈서버 PostgreSQL 접속.
- 마이그레이션은 **PG 전용 한 벌**(`db/migration/V1__init.sql`)로 test·prod 동일 검증(H2 미사용 — 부분 유니크 `WHERE`·`TIMESTAMPTZ`).

### 스키마 소유권

- **Flyway가 스키마(`jwgasul`)·테이블을 소유.** 스키마 변경은 새 `V{n}__*.sql`로만(기존 파일 수정 금지).
- JPA `ddl-auto: validate` — 엔티티↔테이블 매핑 불일치를 기동 시 잡는다. 엔티티 변경 시 대응 마이그레이션 동반.

### 인증 (Spring Security 7)

- 폼 로그인 + 세션. `SecurityConfig`는 **람다 DSL만**(3.x `.and()` 금지).
- 로그인 실패 잠금 = **username 기준**(`LoginAttemptService`, 인메모리) — IP 아님(Cloudflare Tunnel 뒤 전원 잠김 방지).
- 실제 IP는 `CF-Connecting-IP` 헤더로 복원(`server.forward-headers-strategy: framework`).

## Key Configuration

- **설정 파일**: `src/main/resources/application.yml`. 기본 문서 = 공통/test, `on-profile: prod` 문서 = 운영 오버라이드. 기본 활성 프로필 `${SPRING_PROFILES_ACTIVE:test}`.
- **커스텀 설정**은 `app.*`(PRD 6.5). 정책값(교육 유효기간·명부 상한·로그인 임계)은 하드코딩 금지, `application.yml` + 환경변수(`${ENV:기본값}`)로 외부화.
- **DB는 읽기·쓰기 연결**(CRUD가 본체 — PRD 명시 예외).
- **seed 계정**: `DataInitializer`가 기동 시 `app.seed.admin.*`로 관리자 1건 생성(없을 때만). 개발 기본 `admin`/`admin`, 운영은 `SEED_ADMIN_PASSWORD` 등 환경변수로 반드시 교체.

## API Endpoints / Entry Points

- 화면 라우팅(PRD 5장): `/login`, `/`(대시보드), `/workers`, `/workers/new`, `/workers/import`(엑셀 일괄등록: `GET` 폼·`POST` 처리·`GET /workers/import/template` 템플릿 다운로드), `/workers/{id}`, `/sites`, `/roster/*`, `/audit`, `/files/**`(인증 필수 이미지 서빙).
- `permitAll`: `/login`, `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`. 나머지 인증 요구(`SecurityConfig`).
- 공통 네비(`fragments/layout :: nav`): 대시보드·근로자·현장·명부·감사 로그. **상단 고정(sticky)** — 앵커 이동(`#photos`·`#accounts`)은 `scroll-mt-28`로 고정 네비 높이만큼 오프셋.

## Important Notes

- **모든 파일 최상단에 파일 역할 주석**, **모든 함수 위에 기능 주석**. 언어는 **한국어**.
- **★ 모바일 완벽 대응(mobile-first) 필수** — 사용자가 주로 핸드폰으로 작업. 새 화면은 **모바일 기준 먼저** 설계:
  - 넓은 표 금지 → 모바일은 **카드/스택**, `md:` 이상에서만 table (근로자 목록 참고: 모바일 카드 + 데스크톱 표).
  - 폼은 모바일 **1열**(`grid-cols-1 sm:grid-cols-2/3`). 입력은 **`text-base sm:text-sm`**(모바일 16px = iOS 확대 방지) + 터치 크게(`py-2.5`).
  - 네비/탭은 좁은 화면에서 **가로 스크롤**(`overflow-x-auto` + `shrink-0`).
- **Spring Boot 4.1 / Framework 7 / Security 7 기준. 3.x 문법 금지.**
  - Security: **람다 DSL만**. 스타터명 다름: `spring-boot-starter-webmvc`(≠`-web`)·`-flyway`·모듈별 `-test`.
  - **test-autoconfigure 패키지 이동**: `@AutoConfigureMockMvc` = `org.springframework.boot.webmvc.test.autoconfigure.*`.
  - **MIME 매핑 API 이동**: 3.x의 `ConfigurableWebServerFactory.setMimeMappings` 없음 → `org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory`의 **`addMimeMappings`**(기본 매핑 보존). `WebMimeConfig` 참고.
- **Security 설정 클래스(`SecurityConfig`)는 변경 시 사람이 검토**(PRD 지침).
- **임베디드 PG(Zonky)** — 테스트도 Docker 불필요. PG 바이너리는 `runtimeOnly`(모든 실행 방식에서 잡히게; `developmentOnly`는 IDE main() 실행 누락). **`darwin-arm64v8` + `linux-arm64v8`**(로컬 mac + Docker CI 리눅스). prod 프로필 미로드라 운영 무영향.
- **Apache POI 버전 수동 관리**(Boot BOM 미관리). `poi-ooxml:5.3.0`.
- **앱 아이콘**: 원본은 `static/images/favicon.svg`(비계 프레임 모티프, slate-800 + teal-400). PNG/ICO는 **`scripts/gen-icon.py`가 생성**(외부 라이브러리 없이 SDF 렌더링 → 16/32/48 ICO + 180/192/512 PNG). 색·형태를 바꾸면 **SVG와 스크립트 상수를 같이 고치고 재생성**한다: `python3 scripts/gen-icon.py src/main/resources/static/images && mv -f .../images/favicon.ico .../static/favicon.ico`. 16px 이하는 대각 가새를 뺀 단순화 기하(`SEGMENTS_SMALL`)로 그린다.
- **홈 화면 추가(PWA-lite)**: `static/images/site.webmanifest` + `apple-touch-icon`(180px) + `theme-color`. 아이콘/매니페스트는 `/images/**`·`/favicon.ico`라 `SecurityConfig` 수정 없이 비인증 접근 가능(로그인 화면에서도 아이콘 표시).
- **메신저 링크 미리보기(Open Graph)**: `layout :: head`에 `og:*`/`twitter:card`, 카드 이미지는 `images/og-image.png`(1200x630, gen-icon.py가 생성). **절대 URL이 필수**라 도메인을 하드코딩하지 않고 `BaseUrlAdvice`(@ControllerAdvice)가 요청에서 복원한 `baseUrl`을 쓴다 — `forward-headers-strategy: framework` 덕에 Cloudflare Tunnel 뒤에서도 `https://도메인`으로 나온다. **og:title/description은 화면별 title이 아닌 고정 문구**(채팅방에 내부 화면명 노출 방지). 크롤러가 읽는 건 로그인 화면(`/login` permitAll)이라 데이터는 노출되지 않는다. 검색 색인은 `robots noindex`로 차단(robots.txt는 두지 않음 — 두면 카카오 스크랩까지 막힘).
- 계좌번호·신분증은 민감정보. 화면 마스킹 + 접근 감사 로그(F-12) 우회 금지. 계좌 전체 노출·계좌 포함 엑셀 반출은 반드시 `audit_log`에 기록.

## Operational Decisions

- **임베디드 PG 기본 프로필**: 개발/실행 시 Docker·외부 PG 준비 불필요. 실 PG 바이너리라 마이그레이션 충실도 유지. **인메모리 Stub 서비스를 안 만드는 이유** = 백엔드가 DB라 embedded PG로 "외부 없이 실 데이터"를 이미 달성(Stub은 백엔드가 외부 시스템일 때만 필요).
- **감사 로그 = 서비스 레이어 명시적 diff**(Envers·`@EntityListeners` 미채택) — `VIEW`·엑셀 반출 등 비-CRUD를 CRUD와 같은 경로로, 수행자·실 IP 주입 위해(PRD F-12).
- **CI/CD (GitHub Actions self-hosted `mac-mini`)**:
  - 멀티스테이지 `Dockerfile`: `build`(bootJar) → `ci`(test) → `runtime`(JRE). CI=`--target ci`, Deploy=`-t jw-gasul`.
  - **비root 실행 필수** — 임베디드 PG(`initdb`)는 root로 못 뜬다. build/ci는 `builder`, runtime은 `appuser`로 실행.
  - **워크플로 `timeout-minutes: 25`** — 빌드 행 시 자동 취소(러너 무한 점유 방지). Docker 빌드는 의존성 캐싱 레이어로 가속.
  - 현재 배포 compose는 `SPRING_PROFILES_ACTIVE=test`(임베디드 PG stub 운영). **실 데이터 전환 시** `prod` + `DB_URL` 등 환경변수만 교체(코드/이미지 변경 없음). test 프로필은 컨테이너 재시작 시 데이터 리셋됨(주의).
- **진행 상황**:
  - ✅ Stage 1 (스캐폴딩·Flyway 전체 스키마·Security 로그인)
  - ✅ Stage 2 (근로자 CRUD 유형별 동적 폼 · 사진 3종 업로드/서빙 · 클라이언트 리사이즈·EXIF·JPEG 변환 · 모바일 대응)
  - ✅ Stage 3 (계좌 관리 — 최대 3개, 주계좌 부분 유니크, 마스킹, 전체보기/삭제 감사 로그)
  - ✅ Stage 4 (목록 필터·검색·고정우선 정렬 · 만료 배지 F-04)
  - ✅ Stage 5 (현장 CRUD — 진행/종료 탭)
  - ✅ Stage 6 (명부 생성 — 랜덤/수동, 고정 항상 포함, 후보 교체, 스냅샷 저장)
  - ✅ Stage 7 (명부 엑셀 출력 A/B/C 모드 — 사진 썸네일·주계좌, C모드 계좌반출 감사) · 샘플 근로자 50명
  - ✅ Stage 8 (대시보드 — 만료 요약·현장/명부 현황)
  - ✅ Stage 9 (감사 로그 F-12 — 근로자/계좌/서류 diff 기록, /audit 조회 화면 필터·페이지네이션, 근로자 상세 변경이력, 보관정리 배치 `AuditRetentionJob` @Profile("!test"))
  - ✅ Stage 10 (Tailwind 정식 빌드 — standalone CLI · 대시보드 도넛/비율막대 시각화 · 백업 스크립트 · UX 보완)
  - ✅ 추가 UX: 생년월일 텍스트 직접입력(자동 하이픈, 달력 팝업 제거) · 사진/계좌 처리 후 해당 섹션 앵커 유지(#photos·#accounts) · 근로자 엑셀 일괄등록(명단+계좌, 템플릿 다운로드·행별 검증/중복스킵, 사진은 미포함)
- **Tailwind 정식 빌드(★ CDN 아님)**: `src/main/css/app.css`(@import "tailwindcss" + @source 템플릿/JS) → **standalone CLI 바이너리**(`build.gradle.kts` `tailwindBuild` 태스크, v4.3.3)로 컴파일 → `build/generated-resources/static/css/app.css` → jar `/static/css/app.css`. `layout.html`은 `<link rel=stylesheet href=/css/app.css>`. `processResources`가 `tailwindBuild` 의존. **새 유틸리티 클래스 추가 시 자동 재스캔**(빌드만 하면 반영). Node/npm 불필요. `TAILWIND_BIN` 환경변수로 바이너리 경로 주입 시 다운로드 스킵(Docker 캐싱용). DaisyUI는 미도입(코드가 유틸리티 클래스 기반).
- **백업**(`scripts/backup.sh`, PRD 6): prod PG `pg_dump`(gzip) + 업로드 디렉터리 tar, 홈서버 로컬만 보관(기본 30일 보관), 외부 반출 금지(반출 시 gpg 필수). cron 예시 스크립트 주석 참조.
- **감사 대상**: WORKER(생성/수정 필드 diff/삭제) · ACCOUNT(생성/수정/삭제/주계좌지정/전체보기 VIEW) · DOCUMENT(업로드/교체/삭제, entityId=workerId) · ROSTER(C모드 엑셀 계좌반출 VIEW). 근로자 상세 이력은 WORKER+DOCUMENT를 workerId로 함께 조회.
- **미완료(향후)**: 엑셀 일괄 **사진** 업로드(스프레드시트 행 매핑 불안정 → ZIP+파일명 키 방식 별도 설계 예정) · 배포 compose 비root/prod 전환 마무리 · 로그인 잠금 지속시간·저장소 확정.
