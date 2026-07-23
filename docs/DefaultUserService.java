package nbss.cm.ldap.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbss.cm.ldap.adapter.Jenkins;
import nbss.cm.ldap.adapter.Ldap;
import nbss.cm.ldap.domain.User;
import nbss.cm.ldap.dto.UserCreateRequest;
import nbss.cm.ldap.dto.UserUpdateRequest;
import nbss.cm.ldap.exception.LdapException;
import nbss.cm.ldap.service.AuditService;
import nbss.cm.ldap.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DefaultUserService implements UserService {

    private final Ldap ldap;
    private final Jenkins jenkins;
    private final AuditService auditService;

    @Override
    public List<User> getAllUsers() {
        return ldap.getAllUsers();
    }

    @Override
    public List<User> searchUsers(String keyword) {
        return ldap.searchUsers(keyword);
    }

    @Override
    public User getUser(String username) {
        return ldap.getUser(username);
    }

    @Override
    public void createUser(UserCreateRequest request) {
        try {
            jenkins.triggerCreateUser(request);
            auditService.logSuccess("CREATE_USER", request.getsAMAccountName(), "사용자 생성 (Jenkins Job)");
            log.info("[USER] 사용자 생성 완료 username={}", request.getsAMAccountName());
        } catch (Exception e) {
            auditService.logFailure("CREATE_USER", request.getsAMAccountName(), "사용자 생성 실패", e.getMessage());
            throw new LdapException("사용자 생성 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateUser(UserUpdateRequest request) {
        try {
            ldap.updateUser(request);
            auditService.logSuccess("UPDATE_USER", request.getsAMAccountName(), "사용자 정보 수정");
            log.info("[USER] 사용자 수정 완료 username={}", request.getsAMAccountName());
        } catch (Exception e) {
            auditService.logFailure("UPDATE_USER", request.getsAMAccountName(), "사용자 수정 실패", e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteUser(String username) {
        try {
            ldap.deleteUser(username);
            auditService.logSuccess("DELETE_USER", username, "사용자 삭제");
            log.info("[USER] 사용자 삭제 완료 username={}", username);
        } catch (Exception e) {
            auditService.logFailure("DELETE_USER", username, "사용자 삭제 실패", e.getMessage());
            throw e;
        }
    }

    @Override
    public String resetPassword(String username, String newPassword) {
        try {
            String result = jenkins.triggerPasswordReset(username, newPassword);
            // 패스워드 초기화 후처리: badPwdCount=0, lockoutTime=0, accountExpires=+3개월
            try {
                ldap.resetPasswordAttributes(username);
            } catch (Exception e) {
                log.warn("[USER] 패스워드 초기화 후처리 실패 username={}: {}", username, e.getMessage());
            }
            auditService.logSuccess("RESET_PASSWORD", username, "패스워드 초기화 (Jenkins Job)");
            return result;
        } catch (Exception e) {
            auditService.logFailure("RESET_PASSWORD", username, "패스워드 초기화 실패", e.getMessage());
            throw e;
        }
    }

    @Override
    public String enableUser(String username) {
        try {
            String result = ldap.enableUser(username);
            auditService.logSuccess("ENABLE_USER", username, "계정 활성화 (LDAP)");
            return result;
        } catch (Exception e) {
            auditService.logFailure("ENABLE_USER", username, "계정 활성화 실패", e.getMessage());
            throw e;
        }
    }

    @Override
    public void disableUser(String username) {
        try {
            ldap.disableUser(username);
            auditService.logSuccess("DISABLE_USER", username, "계정 비활성화");
            log.info("[USER] 계정 비활성화 완료 username={}", username);
        } catch (Exception e) {
            auditService.logFailure("DISABLE_USER", username, "계정 비활성화 실패", e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean authenticate(String username, String password) {
        return ldap.authenticate(username, password);
    }

    @Override
    public Map<String, String> getAvailableGroups() {
        return ldap.searchGroups();
    }

    @Override
    public void updateLastLogon(String username) {
        // AD 도메인 컨트롤러가 인증 시 lastLogon을 자동 갱신하므로 운영에서는 no-op
        log.debug("[USER] lastLogon은 AD가 자동 갱신 username={}", username);
    }
}
