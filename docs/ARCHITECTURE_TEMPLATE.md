# 프로필 기반 Stub/Default 아키텍처 — 이식 가이드

> 이 문서는 **NBSS-CM-LDAP** 프로젝트의 핵심 아키텍처 패턴을 다른 Spring Boot 프로젝트에 그대로 옮길 수 있도록 정리한 참고 문서입니다.
> Claude Code에게 "이 문서의 패턴대로 구조를 잡아줘"라고 지시하면 됩니다.

---

## 1. 한 줄 요약

**하나의 서비스 인터페이스에 대해, `@Profile("test")` Stub 구현체와 `@Profile("!test")` Default(운영) 구현체를 둘 다 만든다.**
Spring이 활성 프로필에 따라 자동으로 한쪽만 빈으로 등록하므로, **외부 시스템(LDAP/DB/외부 API) 없이도 `test` 프로필만으로 앱 전체가 완전히 동작**한다.

```
활성 프로필 = test   → Stub 구현체 로드 → 인메모리 Mock 데이터 → 외부 연동 0개, 로컬에서 즉시 실행
활성 프로필 = !test  → Default 구현체 로드 → 실제 외부 시스템(LDAP/API/DB) 연동
```

핵심 이점:
- **로컬 개발/데모/UI 작업**을 외부 인프라 연결 없이 수행 (Stub = 살아있는 Mock 서버)
- **테스트 코드가 Stub을 그대로 재사용** (별도 Mock 프레임워크 최소화)
- **운영 코드(Default)와 개발 코드(Stub)가 동일 인터페이스로 강제 정합** — 계약(interface)이 어긋나면 컴파일 실패
- 신규 기능은 항상 **인터페이스 → Stub → Default** 순으로 개발 (UI를 Stub로 먼저 완성 → 운영 연동은 나중에)

---

## 2. 패키지 구조

```
src/main/java/{basePackage}/
├── config/          # SecurityConfig, @ConfigurationProperties, 인증 프로바이더, StubDataInitializer
├── controller/      # 3-tier: ViewController(SSR) + HtmxController(fragment) + ApiController(JSON)
├── service/         # ★ 인터페이스만 (UserService, MailService, ...)
│   └── impl/        # ★ StubXxxService(@Profile("test")) + DefaultXxxService(@Profile("!test"))
├── adapter/         # 외부 시스템 통신 캡슐화 (Ldap, Jenkins, Svn ...) — @Profile("!test")만
├── domain/          # 도메인 모델 (User ...)
├── dto/             # 요청/응답 DTO (Bean Validation)
├── mapper/          # 외부 데이터 → 도메인 변환 (LDAP Attributes → User)
├── repository/      # JPA 리포지토리 (프로필 무관, 인메모리 H2 등)
├── exception/       # 커스텀 예외 + @ControllerAdvice
├── scheduler/       # @Scheduled 배치 (@Profile("!test"))
└── util/            # 순수 유틸리티 (프로필 무관, static)

src/main/resources/
├── application.yml          # 공통 + 기본값. spring.profiles.active=${SPRING_PROFILES_ACTIVE:test}
├── application-test.yml     # test 프로필 오버라이드 (Stub 전용 설정)
├── application-local.yml    # 개인 로컬 오버라이드
└── mock-users.json          # Stub이 로드하는 Mock 데이터
```

**분류 기준**
| 계층 | 프로필 분기 | 이유 |
|------|-----------|------|
| `service/impl/*` | ✅ Stub + Default 쌍 | 외부 연동 여부가 프로필로 갈림 |
| `adapter/*` | ❌ Default(`!test`)만 | 외부 시스템 전용. test에선 아예 로드 안 됨 |
| `repository`, `util`, `domain`, `dto`, `mapper` | ❌ 프로필 무관 | 외부 의존 없음 |
| `scheduler/*` | ❌ `!test`만 (보통) | 운영에서만 도는 배치 |

---

## 3. 핵심 패턴 3종 (복붙 템플릿)

### 3-1. 서비스 인터페이스 (계약)

```java
package {basePackage}.service;

public interface UserService {
    List<User> getAllUsers();
    User getUser(String username);
    void createUser(UserCreateRequest request);
    boolean authenticate(String username, String password);
    // ... 순수 도메인 언어로만 정의. LDAP/HTTP 같은 구현 세부는 절대 노출하지 않는다.
}
```

> **규칙**: 인터페이스 시그니처에 외부 기술(LDAP `Attributes`, HTTP `ResponseEntity` 등)이 새어나오면 안 된다. Stub이 그걸 흉내낼 수 없게 되기 때문.

### 3-2. Stub 구현체 (`@Profile("test")`)

```java
package {basePackage}.service.impl;

@Slf4j
@Service
@Profile("test")               // ★ test 프로필에서만 빈 등록
@RequiredArgsConstructor
public class StubUserService implements UserService {

    private static final String DEFAULT_PASSWORD = "password";  // 전 계정 동일 (데모용)
    private static final String MOCK_DATA_FILE = "mock-users.json";

    private final AuditService auditService;                    // 다른 서비스 주입도 자유롭게
    private final Map<String, User> userStore = new ConcurrentHashMap<>();  // 인메모리 상태

    @PostConstruct
    public void init() {
        // classpath의 mock-*.json 로드 → userStore 채우기
        try (InputStream is = new ClassPathResource(MOCK_DATA_FILE).getInputStream()) {
            List<Map<String,Object>> rows =
                new ObjectMapper().readValue(is, new TypeReference<>() {});
            // ... User.builder()로 변환 후 userStore.put()
        } catch (IOException e) {
            throw new IllegalStateException("Mock 데이터 로드 실패", e);
        }
    }

    @Override public List<User> getAllUsers() { return new ArrayList<>(userStore.values()); }
    @Override public User getUser(String u) { return userStore.get(u); }
    @Override public boolean authenticate(String u, String p) {
        return DEFAULT_PASSWORD.equals(p) && userStore.containsKey(u);
    }
    // ... 인메모리로 CRUD 흉내. 실제 외부 호출은 로그만 남기고 no-op 처리해도 됨
}
```

### 3-3. Default 구현체 (`@Profile("!test")`)

```java
package {basePackage}.service.impl;

@Slf4j
@Service
@Profile("!test")              // ★ test가 아닌 모든 프로필에서 빈 등록
@RequiredArgsConstructor
public class DefaultUserService implements UserService {

    private final Ldap ldap;            // ★ adapter 주입 (외부 시스템 통신 캡슐화)
    private final Jenkins jenkins;
    private final AuditService auditService;

    @Override public List<User> getAllUsers() { return ldap.getAllUsers(); }
    @Override public User getUser(String u) { return ldap.getUser(u); }
    @Override public void createUser(UserCreateRequest req) {
        jenkins.triggerCreateUser(req);                          // 실제 외부 호출
        auditService.logSuccess("CREATE_USER", req.getId(), "...");
    }
    // ... adapter에 위임. 컨트롤러는 이 클래스인지 Stub인지 전혀 모른다
}
```

> **규칙**: 컨트롤러/다른 서비스는 언제나 **인터페이스 타입**(`UserService`)으로만 주입받는다. 구현체를 직접 참조하면 프로필 분기가 깨진다.

---

## 4. 프로필 설정 배선

### application.yml (공통 + 기본값)
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:test}   # ★ 미지정 시 test = 안전한 기본값

app:
  admin-id: ${ADMIN_ID:cmadm}                # 환경변수 오버라이드 패턴 ${ENV:default}

ldap:                                        # 운영 전용 설정 (test에선 안 읽힘)
  url: ${LDAP_URL:ldap://...}
```

### application-test.yml (Stub 전용 오버라이드)
```yaml
spring:
  thymeleaf:
    cache: false            # 개발 편의

app:
  admin-id: admin           # test에선 admin/admin으로 관리자 로그인

logging:
  level:
    {basePackage}: DEBUG
```

### 실행
```bash
# 기본 = test 프로필 (외부 연동 불필요, 즉시 실행)
mvn spring-boot:run

# 운영 프로필 (LDAP/API 필요)
mvn spring-boot:run -Dspring-boot.run.profiles=default
# 또는
SPRING_PROFILES_ACTIVE=default java -jar app.jar
```

---

## 5. 프로필로 갈리지 않는 조력 컴포넌트들

### Adapter 패턴 (외부 시스템 통신 캡슐화, `!test`만)
```java
@Component
@Profile("!test")
public class Ldap {
    @PostConstruct void init() { /* 연결 설정 */ }

    // 보일러플레이트 제거용 템플릿 메서드
    private <T> T executeWithContext(LdapOperation<T> op) {
        DirContext ctx = null;
        try { ctx = createContext(); return op.execute(ctx); }
        finally { if (ctx != null) ctx.close(); }
    }
    @FunctionalInterface interface LdapOperation<T> { T execute(DirContext ctx) throws Exception; }
}
```
> Adapter는 **Default 구현체가 위임하는 대상**. Stub은 adapter를 아예 주입받지 않으므로 test 프로필엔 이 빈이 없어도 문제없다.

### 인증 프로바이더 (런타임 프로필 감지)
```java
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {
    @Value("${app.admin-id}") private String adminId;
    private final Environment environment;

    @Override public Authentication authenticate(Authentication auth) {
        boolean isTest = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (isTest) authenticateWithStub(...);   // Stub 인증
        else        authenticateWithLdap(...);   // 실제 LDAP bind
        String role = username.equals(adminId) ? "ROLE_ADMIN" : "ROLE_USER";
        // ...
    }
}
```
> 프로바이더 하나가 `Environment.getActiveProfiles()`로 런타임 분기하는 방식(빈을 둘로 나누지 않음)도 유효한 변형.

### StubDataInitializer (test 데모 데이터 시딩)
```java
@Component
@Profile("test")
public class StubDataInitializer implements CommandLineRunner {
    @Override public void run(String... args) {
        // 앱 기동 시 감사 로그/차트 데모용 인메모리 데이터 자동 생성
    }
}
```

---

## 6. 이 아키텍처를 신규 프로젝트에 적용하는 순서

Claude Code에게 아래 순서로 지시하면 됩니다.

1. **패키지 골격 생성** — 위 §2 구조대로 디렉토리 생성
2. **프로필 배선** — `application.yml`에 `spring.profiles.active=${SPRING_PROFILES_ACTIVE:test}` + `application-test.yml` 생성
3. **첫 서비스는 인터페이스부터** — `service/XxxService.java` (순수 도메인 시그니처)
4. **StubXxxService 작성** (`@Profile("test")`) — 인메모리 `ConcurrentHashMap` + `@PostConstruct`에서 `mock-*.json` 로드
5. **mock-*.json 작성** — 페이지네이션/차트 등 UI 데모에 충분한 양(예: 50건)
6. **UI/컨트롤러를 Stub만으로 완성** — 이 시점엔 외부 시스템 0개
7. **DefaultXxxService 작성** (`@Profile("!test")`) + **adapter** — 실제 외부 연동은 마지막에
8. 반복: 새 기능마다 **인터페이스 → Stub → (UI 완성) → Default** 사이클

### 체크리스트 (패턴 준수 검증)
- [ ] 모든 서비스가 인터페이스 + Stub + Default 3종 세트인가?
- [ ] Stub은 `@Profile("test")`, Default는 `@Profile("!test")`인가?
- [ ] 컨트롤러/서비스가 **인터페이스 타입**으로만 주입받는가? (구현체 직접 참조 금지)
- [ ] 인터페이스 시그니처에 외부 기술 타입(Attributes/ResponseEntity 등)이 안 새는가?
- [ ] `mvn spring-boot:run` (기본 test) 이 **외부 시스템 없이** 뜨는가?
- [ ] adapter/scheduler에 `@Profile("!test")`가 붙어 test에서 로드 안 되는가?
- [ ] 환경변수 오버라이드가 `${ENV_VAR:기본값}` 패턴인가?

---

## 7. 참고 — 이 프로젝트의 실제 매핑표

| 서비스 인터페이스 | Stub (`test`) | Default (`!test`) | Default가 쓰는 adapter |
|---|---|---|---|
| `UserService` | `StubUserService` (mock-users.json 50명) | `DefaultUserService` | `Ldap`, `Jenkins` |
| `PermissionService` | `StubPermissionService` (인메모리) | `DefaultPermissionService` | `Jenkins`, `Svn` |
| `MailService` | `StubMailService` (로그만) | `DefaultMailService` | RestClient → MAIL-AGENT |
| `VerificationCodeService` | `StubVerificationCodeService` | `DefaultVerificationCodeService` | (인메모리, 동일) |
| `SchedulerService` | `StubSchedulerService` (Stub 2건) | `DefaultSchedulerService` | `List<ManagedScheduler>` |
| `AuditService` | `DefaultAuditService` (프로필 무관, H2) | 동일 | JPA Repository |

> `AuditService`처럼 **양쪽 공통(인메모리 H2 등)이면 굳이 Stub/Default를 나누지 않아도 된다.** 외부 시스템 의존이 없으면 단일 구현으로 충분 — 무조건 둘로 쪼개는 게 목적이 아니라, **외부 연동 유무가 프로필로 갈릴 때만** 나눈다.
