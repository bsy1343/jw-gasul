# 아키텍처 이식 가이드 — Layered + Factory + Stub/Default 프로필 패턴

> **목적**: DevForge에서 검증된 백엔드 아키텍처 패턴을 **새 Spring Boot 프로젝트에 그대로 이식**하기 위한 참조 문서.
> Claude Code 에게 이 문서를 그대로 던지고 "이 패턴대로 구조 잡아줘 / 리팩토링해줘" 라고 지시하면 된다.
> 스택 전제: **Spring Boot 3.x + JDK 21**. (다른 언어/프레임워크여도 3계층·Factory·프로필 분기 개념은 그대로 적용 가능)

---

## 0. 이 패턴이 푸는 문제 (왜 이렇게 하나)

| 문제 | 이 패턴의 해법 |
|------|--------------|
| 외부 시스템(GitLab, Jenkins, Slack…) 없이 로컬/CI에서 앱을 못 돌림 | **Stub 구현체** — `test` 프로필에서 Mock 반환, 외부 서버 0개로 부팅 |
| 같은 기능인데 벤더가 여럿 (GitLab vs GitHub) | **Factory 패턴** — 구현체 전부 빈 등록 후 런타임 선택 |
| 시간이 지나면 레이어가 섞여 스파게티가 됨 | **ArchUnit 테스트**로 레이어 의존성을 CI에서 강제 |
| 테스트가 외부 API에 의존해 느리고 불안정 | 테스트는 자동으로 Stub 주입 → 빠르고 결정적 |

**한 줄 요약**: 인터페이스 하나에 구현체를 두 벌(Stub / 실제) 만들고, **Spring 프로필(`test` vs `!test`)로 어느 쪽 빈이 뜰지 결정**한다. 벤더가 여럿이면 그 위에 Factory를 얹는다.

---

## 1. 레이어 아키텍처 (뼈대)

```
Controller  →  Service  →  Repository  →  DB
                  │
                  └────→  Adapter  →  외부 시스템 (HTTP/gRPC/…)
```

**단방향 규칙 (역방향·건너뛰기 전부 금지)**:
- Controller 는 Service 만 호출 — Repository 직접 접근 ❌
- Service 는 Repository / Adapter / 다른 Service 호출 ⭕
- Repository 는 아무 레이어에도 의존 안 함 (DB 접근만)
- **외부 시스템 통신은 오직 Adapter 레이어** — Service 가 HTTP 클라이언트를 직접 들면 안 됨

### 패키지 구조 (Package-by-Layer + 도메인 그룹핑)

```
com.{app}/
├── controller/          # HTTP 진입점 (URL/메뉴 단위)
│   ├── admin/           # 하위 도메인별 서브패키지
│   └── api/             # REST API 전용
├── service/
│   ├── {domain}/        # 예: project/, dashboard/, cicd/
│   │   ├── XxxService.java        # 인터페이스
│   │   └── impl/                  # ← Stub/Default 구현체가 여기 모임
│   │       ├── StubXxxService.java
│   │       └── DefaultXxxService.java
│   └── admin/connection/impl/     # Factory 구현체 모음
├── adapter/             # 외부 API 래퍼 (GitLabAdapter, JenkinsAdapter…)
├── repository/          # Spring Data JPA 인터페이스
├── domain/              # Entity, Value Object, Enum (suffix 없음)
├── dto/                 # *Dto
└── common/              # config/ · exception/ · util/ · stub/
```

**원칙**:
- Controller = URL 메뉴 단위 (개수 억제, SSR 친화)
- Service = `인터페이스 + impl/{Stub, Default}` — 이게 프로필 교체 지점
- 순수 내부 로직(단순 DB CRUD)이라 벤더 분기가 없어도, **외부 I/O가 끼면 인터페이스로 분리**해서 Stub 가능하게 한다

> **왜 `repository/`·`dto/` 를 `domain/` 밑에 안 넣나 (자주 나오는 질문)**
> 이 구조의 핵심은 **top-level 패키지 하나 = 레이어 하나 = ArchUnit 규칙 하나** 라는 1:1 매핑이다. 이걸 깨면 강제력이 약해진다.
> - **`domain/` 은 "전 레이어 접근 허용" 이라는 특수 지위** — 엔티티/VO/Enum 처럼 어디서나 참조돼야 하는 것만 둔다. 여기에 `repository` 를 넣으면 `Controller` 가 Repository 를 직접 주입해도 ArchUnit 이 못 잡아, *"Controller→Repository 직접 접근 금지"* 규칙이 무력화된다.
> - **패턴 충돌** — `..domain..` 은 경로에 `domain` 이 든 패키지를 전부 매칭하므로 `domain.repository` 가 Domain·Repository 두 레이어에 동시 소속돼 `layeredArchitecture()` 가 깨진다.
> - **`dto` 는 도메인이 아니다** — DTO 는 요청/응답을 실어나르는 경계(boundary) 객체라 비즈니스 모델인 `domain/` 과 성격이 다르다. 반드시 top-level 로 분리.
> - DDD "도메인 모듈" 스타일(`domain/model/` + `domain/repository/`)을 정 쓰려면, ArchUnit 패턴을 `..domain.model..` / `..domain.repository..` 로 좁혀 명시해야 한다. 이 가이드는 단순함을 위해 **평면 top-level 을 채택**한다.

---

## 2. Stub / Default 프로필 분리 ★핵심★

같은 인터페이스에 구현체 두 벌. **어느 빈이 뜨는지는 활성 프로필이 결정**한다.

### 인터페이스

```java
// SCM 서비스 인터페이스 — 소스 저장소 생성/브랜치 관리 등
package com.app.service.scm;

public interface ScmService {
    String getScmType();                                   // Factory 키 (벤더 식별자)
    String createSourceRepo(SystemEntity system, String group, String name, String desc);
    void   protectBranches(SystemEntity system, String repoId, String... branches);
    // 비즈니스 메서드 첫 파라미터는 컨텍스트 엔티티(SystemEntity 등)로 통일
}
```

### Stub 구현체 — `@Profile("test")`

```java
// SCM 스텁 서비스 — test 프로필에서 활성화. 외부 API 없이 Mock 값 반환
package com.app.service.scm.impl;

@Profile("test")          // ← test 프로필일 때만 빈 등록
@Service
public class StubScmService implements ScmService {

    private static final Logger log = LoggerFactory.getLogger(StubScmService.class);

    @Override
    public String getScmType() { return "stub"; }          // Factory엔 "stub"으로 등록

    @Override
    public String createSourceRepo(SystemEntity system, String group, String name, String desc) {
        log.info("[Stub SCM] 소스 레포 생성: group={}, project={}", group, name);
        return "stub-source-repo-" + name;                 // 가짜 식별자 (형식은 실제와 동일하게)
    }

    @Override
    public void protectBranches(SystemEntity system, String repoId, String... branches) {
        log.info("[Stub SCM] 브랜치 보호 설정: repo={}, branches={}", repoId, String.join(",", branches));
        // void 메서드는 로그만 — 외부 호출 없음
    }
}
```

**Stub 규칙**:
- 생성자에 Adapter 주입 **안 함** (외부 I/O 없으니까)
- 반환값은 `"stub-{type}-{id}"` 처럼 **형식은 실제와 동일**하게 — 상위 로직이 Stub인지 몰라도 돌아가야 함
- `void` 메서드는 로그만 남기고 종료
- 조회 메서드는 그럴듯한 Mock 데이터 반환 (테스트/데모가 자연스럽도록)

### Default 구현체 — `@Profile("!test")`

```java
// GitLab SCM 서비스 — test 외 프로필에서 활성화. 실제 GitLab API 호출
package com.app.service.scm.impl;

@Profile("!test")         // ← test가 아닐 때(dev/prod 등)만 빈 등록
@Service
public class GitLabScmService implements ScmService {

    private final GitLabAdapter adapter;                   // 외부 API는 Adapter로만

    public GitLabScmService(GitLabAdapter adapter) { this.adapter = adapter; }

    @Override
    public String getScmType() { return "GITLAB"; }        // Factory 선택 키

    @Override
    public String createSourceRepo(SystemEntity system, String group, String name, String desc) {
        var conn = system.getScmConnection();              // 엔티티에서 연결정보 추출
        return adapter.createProject(conn.getUrl(), conn.getApiToken(), group, name, desc);
    }
    // ...
}
```

### 프로필 설정 (`application.yml`)

```yaml
# 기본 프로필을 test로 → clone 직후 외부 서버 0개로 바로 부팅됨
spring:
  profiles:
    active: test
  datasource:
    url: jdbc:h2:mem:app;DB_CLOSE_DELAY=-1     # test는 H2 인메모리
```

실행:
```bash
./mvnw spring-boot:run                                              # test (Stub + H2)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod             # prod (Default + 실 DB)
./mvnw test                                                        # 테스트 → 자동 test 프로필 → Stub
```

> **핵심 효과**: `git clone` → `./mvnw spring-boot:run` 이면 끝. 외부 시스템·시크릿 세팅 없이 전체 앱이 뜨고, 모든 테스트가 결정적으로 돈다.

---

## 3. Factory 패턴 (같은 기능 · 여러 벤더)

벤더가 하나면 Factory 불필요 — Stub/Default 프로필 분기만으로 충분.
**벤더가 둘 이상(GitLab/GitHub, Jenkins/GitHub Actions…)** 일 때만 Factory를 얹는다.

```java
// SCM 서비스 팩토리 — 시스템 설정(scmType)에 따라 구현체 반환
@Component
public class ScmServiceFactory {

    private final Map<String, ScmService> serviceMap;

    // 등록된 모든 ScmService 빈을 List로 주입받아 → getScmType() 키로 Map 구축
    public ScmServiceFactory(List<ScmService> services) {
        this.serviceMap = services.stream()
            .collect(Collectors.toMap(ScmService::getScmType, Function.identity()));
        log.info("[ScmServiceFactory] 등록된 SCM 서비스: {}", serviceMap.keySet());
    }

    // 시스템의 SCM 유형에 맞는 구현체를 런타임 선택
    public ScmService get(SystemEntity system) {
        String key = system.getScmConnection().getScmType().getValue();  // "GITLAB" / "GITHUB"
        ScmService svc = serviceMap.get(key);
        if (svc == null) throw new IllegalStateException("지원하지 않는 SCM 유형: " + key);
        return svc;
    }
}
```

**동작 방식**:
- 모든 구현체가 **동시에 빈으로 등록**됨 (프로필이 허용하는 것만) — `@Profile("test")` 면 test 에선 Stub만, `!test` 면 GitLab·GitHub 둘 다 뜸
- Factory 생성자가 `List<Interface>` 를 받아 식별자(`getScmType()`)를 키로 `Map` 구성
- 호출부는 `factory.get(system)` 으로 **if/switch 없이** 적절한 구현체 획득

**Factory 규칙**:
- 인터페이스에 식별자 메서드(`getXxxType()`) 필수 → Map 키
- 비즈니스 메서드 첫 파라미터는 컨텍스트 엔티티로 통일 (구현체가 거기서 연결정보 추출)
- test 프로필에선 Stub 하나만 등록되므로, Factory 는 test 에서도 그대로 동작 (Stub이 `"stub"` 키로 잡힘 — 필요 시 Stub의 키를 실제 벤더 키로 반환하게 해서 라우팅 검증)

---

## 4. 외부 연동 엔티티 패턴 (선택)

연결 정보(URL/토큰)를 DB로 관리한다면:

```java
@Entity
@Table(name = "scm_connections", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class ScmConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;                          // unique

    @Column(nullable = false, length = 500)
    private String url;

    @Convert(converter = AesEncryptConverter.class)   // ★ 토큰/비번은 컬럼 암호화
    @Column(length = 1000)
    private String apiToken;

    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false)                     private LocalDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

- 민감 필드는 **`@Convert` 컬럼 암호화** — 평문 저장·로그 출력 금지
- 삭제 시 **참조 보호**: `countByXxxConnectionId(id) > 0` 이면 예외 던지고 삭제 거부

---

## 5. 아키텍처 강제 — ArchUnit 테스트

레이어 규칙은 문서로만 두면 반드시 무너진다. **테스트로 CI에서 강제**한다.

```java
// pom.xml: com.tngtech.archunit:archunit-junit5 (test scope)
public class LayerDependencyTest {

    static JavaClasses classes;
    @BeforeAll static void setup() {
        classes = new ClassFileImporter().importPackages("com.app");
    }

    @Test void controllersShouldNotAccessRepositories() {
        classes().that().resideInAPackage("..controller..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackage("..repository..")
            .check(classes);
    }

    @Test void layeredArchitectureShouldBeRespected() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Adapter").definedBy("..adapter..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Service")
            .check(classes);
    }
}
```

추가로 `NamingConventionTest` 로 suffix 규칙(Controller/Service/Repository/Adapter/*Dto)도 강제 가능.
`config/security/aspect/util` 같은 횡단 관심사는 레이어드 검증에서 제외하고 개별 규칙으로만 본다.

---

## 6. 코딩 규약 (일관성)

| 항목 | 규칙 |
|------|------|
| 네이밍 | `*Controller` / `*Service` / `*Factory` / `*Repository` / `*Adapter` / `*Dto`. Entity 는 suffix 없음(명사) |
| 주석 | 파일 최상단 역할 한 줄 + public 메서드 위 `/** 기능 */` |
| 로깅 | `log.info("[서비스명] 메시지: param={}", param)` — 컨텍스트 태그 |
| 보안 | 토큰/비번 하드코딩·로그 출력 금지, DB 컬럼은 `@Convert` 암호화 |
| 커밋 | `<type>: <subject>` (feat/fix/refactor/docs/test/chore) |

---

## 7. Claude Code 에게 시키는 법 (마이그레이션 절차)

새 프로젝트에서 이 문서를 첨부하고 아래 순서로 지시하면 안전하다. **한 번에 다 하지 말고 단계별로 커밋**.

1. **뼈대부터**: "이 문서의 1번 패키지 구조로 디렉토리/베이스 패키지 정리해줘. 기존 코드 이동은 아직 하지 마."
2. **ArchUnit 먼저**: "5번의 `LayerDependencyTest` 를 추가하고 돌려줘. 지금 몇 개 위반하는지 리포트만 해줘." → 현재 부채 가시화
3. **한 도메인씩 인터페이스 추출**: "XxxService 에서 외부 I/O 부분을 인터페이스로 분리하고, 기존 구현을 `DefaultXxxService(@Profile("!test"))` 로, 외부 호출을 `XxxAdapter` 로 옮겨줘."
4. **Stub 생성**: "같은 인터페이스의 `StubXxxService(@Profile("test"))` 를 만들어줘. 반환 형식은 Default 와 동일하게, 외부 I/O 없이 Mock 으로."
5. **프로필 세팅**: "`application.yml` 기본 프로필을 test 로, H2 인메모리로 잡아줘. `./mvnw spring-boot:run` 만으로 뜨는지 확인해줘."
6. **벤더 여럿이면 Factory**: "XxxService 구현체가 N개니 3번의 Factory 패턴 적용해줘."
7. **반복 + 강제**: 도메인마다 3~6 반복. 마지막에 ArchUnit 초록 확인.

> **팁**: 이 문서를 새 프로젝트의 `CLAUDE.md` 나 `docs/ARCHITECTURE.md` 로 넣어두면, 이후 Claude Code 세션이 매번 이 규약을 자동으로 따른다. `.claude/rules/backend.md` 처럼 path-scoped rule 로 넣으면 Java 파일 편집 시 자동 로드된다.

---

## 8. 최종 체크리스트

**레이어**
- [ ] Controller→Service→Repository 단방향만
- [ ] 외부 통신은 Adapter 패키지만
- [ ] `LayerDependencyTest` / `NamingConventionTest` 초록

**Stub/Default**
- [ ] 외부 I/O 있는 Service 는 인터페이스로 분리됨
- [ ] Stub: `@Profile("test")`, Adapter 주입 없음, Mock 반환
- [ ] Default: `@Profile("!test")`, Adapter 생성자 주입
- [ ] 두 구현체가 동일 인터페이스 구현, 반환 형식 동일
- [ ] `application.yml` 기본 프로필 test + H2 → clone 후 즉시 부팅

**Factory (벤더 2+ 일 때만)**
- [ ] 인터페이스에 식별자 메서드 존재
- [ ] Factory 가 `List<Interface>` → `Map` 구축
- [ ] 호출부는 `factory.get(context)` 만 사용 (if/switch 없음)

**보안**
- [ ] 민감 필드 `@Convert` 암호화, 하드코딩·로그 출력 없음
- [ ] 삭제 시 참조 보호