package nbss.cm.ldap.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbss.cm.ldap.domain.User;
import nbss.cm.ldap.dto.UserCreateRequest;
import nbss.cm.ldap.dto.UserUpdateRequest;
import nbss.cm.ldap.exception.LdapException;
import nbss.cm.ldap.service.AuditService;
import nbss.cm.ldap.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("test")
@RequiredArgsConstructor
public class StubUserService implements UserService {

    private static final String DEFAULT_PASSWORD = "password";
    private static final String MOCK_DATA_FILE = "mock-users.json";

    private final AuditService auditService;
    private final Map<String, User> userStore = new ConcurrentHashMap<>();
    private final Map<String, String> passwordStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource(MOCK_DATA_FILE).getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> mockUsers = mapper.readValue(is, new TypeReference<>() {});

            LocalDateTime now = LocalDateTime.now();

            for (Map<String, Object> data : mockUsers) {
                String username = (String) data.get("sAMAccountName");
                int createdMonthsAgo = (int) data.get("createdMonthsAgo");

                @SuppressWarnings("unchecked")
                List<String> memberOf = data.get("memberOf") != null
                        ? (List<String>) data.get("memberOf")
                        : Collections.emptyList();

                User.UserBuilder builder = User.builder()
                        .sAMAccountName(username)
                        .name(username)
                        .cn(username)
                        .givenName((String) data.get("givenName"))
                        .mail((String) data.get("mail"))
                        .company((String) data.get("company"))
                        .description((String) data.get("description"))
                        .telephoneNumber((String) data.get("telephoneNumber"))
                        .distinguishedName("cn=" + username + ",cn=Users,dc=cm,dc=nbss,dc=kt,dc=com")
                        .userAccountControl((String) data.get("userAccountControl"))
                        .whenCreated(now.minusMonths(createdMonthsAgo))
                        .whenChanged(now.minusDays(1))
                        .lastLogon(now.minusHours(2))
                        .pwdLastSet(now.minusDays(15))
                        .memberOf(memberOf);

                if (data.containsKey("accountExpiresMonthsAgo")) {
                    builder.accountExpires(now.minusMonths((int) data.get("accountExpiresMonthsAgo")));
                }
                if (data.containsKey("accountExpiresDaysAgo")) {
                    builder.accountExpires(now.minusDays((int) data.get("accountExpiresDaysAgo")));
                }
                if (data.containsKey("accountExpiresMonthsLater")) {
                    builder.accountExpires(now.plusMonths((int) data.get("accountExpiresMonthsLater")));
                }
                if (Boolean.TRUE.equals(data.get("locked"))) {
                    builder.lockoutTime(now.minusHours(3));
                }
                if (data.containsKey("badPwdCount")) {
                    builder.badPwdCount((String) data.get("badPwdCount"));
                }

                userStore.put(username, builder.build());
                passwordStore.put(username, (String) data.getOrDefault("password", DEFAULT_PASSWORD));
            }

            log.info("[STUB] Stub UserService initialized with {} mock users from {}", userStore.size(), MOCK_DATA_FILE);
        } catch (Exception e) {
            log.error("[STUB] Mock 데이터 로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Mock 데이터 로드 실패", e);
        }
    }

    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(userStore.values());
    }

    @Override
    public List<User> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllUsers();
        }
        String kw = keyword.toLowerCase();
        return userStore.values().stream()
                .filter(u -> contains(u.getName(), kw)
                        || contains(u.getsAMAccountName(), kw)
                        || contains(u.getGivenName(), kw)
                        || contains(u.getMail(), kw)
                        || contains(u.getCompany(), kw))
                .collect(Collectors.toList());
    }

    @Override
    public User getUser(String username) {
        User user = userStore.get(username);
        if (user == null) {
            throw new LdapException("사용자를 찾을 수 없습니다: " + username);
        }
        return user;
    }

    @Override
    public void createUser(UserCreateRequest request) {
        if (userStore.containsKey(request.getsAMAccountName())) {
            throw new LdapException("이미 존재하는 사용자: " + request.getsAMAccountName());
        }
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .sAMAccountName(request.getsAMAccountName())
                .name(request.getsAMAccountName())
                .cn(request.getsAMAccountName())
                .givenName(request.getGivenName())
                .mail(request.getMail())
                .company(request.getCompany())
                .description(request.getDescription())
                .telephoneNumber(request.getTelephoneNumber())
                .distinguishedName("cn=" + request.getsAMAccountName() + ",cn=Users,dc=cm,dc=nbss,dc=kt,dc=com")
                .userAccountControl("512")
                .whenCreated(now)
                .whenChanged(now)
                .memberOf(request.getMemberOfList())
                .build();
        userStore.put(request.getsAMAccountName(), user);
        auditService.logSuccess("CREATE_USER", request.getsAMAccountName(), "사용자 생성 (Stub)");
        log.info("[STUB] User created: {}", request.getsAMAccountName());
    }

    @Override
    public void updateUser(UserUpdateRequest request) {
        User user = getUser(request.getsAMAccountName());
        if (request.getGivenName() != null) user.setGivenName(request.getGivenName());
        if (request.getMail() != null) user.setMail(request.getMail());
        if (request.getCompany() != null) user.setCompany(request.getCompany());
        if (request.getDescription() != null) user.setDescription(request.getDescription());
        if (request.getTelephoneNumber() != null) user.setTelephoneNumber(request.getTelephoneNumber());
        if (request.getMemberOf() != null && !request.getMemberOf().isBlank()) user.setMemberOf(request.getMemberOfList());
        auditService.logSuccess("UPDATE_USER", request.getsAMAccountName(), "사용자 수정 (Stub)");
        log.info("[STUB] User updated: {}", request.getsAMAccountName());
    }

    @Override
    public void deleteUser(String username) {
        if (userStore.remove(username) == null) {
            throw new LdapException("사용자를 찾을 수 없습니다: " + username);
        }
        auditService.logSuccess("DELETE_USER", username, "사용자 삭제 (Stub)");
        log.info("[STUB] User deleted: {}", username);
    }

    @Override
    public String resetPassword(String username, String newPassword) {
        User user = getUser(username);
        // 패스워드 초기화 후처리: lockoutTime=null, accountExpires=+3개월 (badPwdCount는 AD 자동 초기화, Stub에서는 인메모리 초기화)
        user.setBadPwdCount("0");
        user.setLockoutTime(null);
        user.setAccountExpires(LocalDateTime.now().plusMonths(3));
        auditService.logSuccess("RESET_PASSWORD", username, "패스워드 초기화 (Stub)");
        log.info("[STUB] Password reset for: {} (lockout해제, accountExpires=+3개월)", username);
        return "패스워드 초기화 성공 (Stub)";
    }

    @Override
    public String enableUser(String username) {
        User user = getUser(username);
        user.setUserAccountControl("512");
        auditService.logSuccess("ENABLE_USER", username, "계정 활성화 (Stub)");
        log.info("[STUB] User enabled: {}", username);
        return "계정 활성화 성공 (Stub)";
    }

    @Override
    public void disableUser(String username) {
        User user = getUser(username);
        user.setUserAccountControl("514");
        auditService.logSuccess("DISABLE_USER", username, "계정 비활성화 (Stub)");
        log.info("[STUB] User disabled: {}", username);
    }

    @Override
    public boolean authenticate(String username, String password) {
        String expected = passwordStore.get(username);
        return expected != null && expected.equals(password);
    }

    @Override
    public Map<String, String> getAvailableGroups() {
        Map<String, String> groups = new java.util.LinkedHashMap<>();
        groups.put("CN=DevTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "DevTeam");
        groups.put("CN=OpsTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "OpsTeam");
        groups.put("CN=QATeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "QATeam");
        groups.put("CN=InfraTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "InfraTeam");
        groups.put("CN=SecurityTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "SecurityTeam");
        groups.put("CN=CloudTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "CloudTeam");
        groups.put("CN=DBATeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "DBATeam");
        groups.put("CN=PlanningTeam,OU=Groups,DC=cm,DC=nbss,DC=kt,DC=com", "PlanningTeam");
        return groups;
    }

    @Override
    public void updateLastLogon(String username) {
        User user = userStore.get(username);
        if (user != null) {
            LocalDateTime now = LocalDateTime.now();
            user.setLastLogon(now);
            user.setLastLogonTimestamp(now);
            log.info("[STUB] lastLogon updated for: {}", username);
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
