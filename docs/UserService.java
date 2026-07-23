package nbss.cm.ldap.service;

import nbss.cm.ldap.domain.User;
import nbss.cm.ldap.dto.UserCreateRequest;
import nbss.cm.ldap.dto.UserUpdateRequest;

import java.util.List;
import java.util.Map;

public interface UserService {

    List<User> getAllUsers();

    List<User> searchUsers(String keyword);

    User getUser(String username);

    void createUser(UserCreateRequest request);

    void updateUser(UserUpdateRequest request);

    void deleteUser(String username);

    String resetPassword(String username, String newPassword);

    String enableUser(String username);

    void disableUser(String username);

    boolean authenticate(String username, String password);

    void updateLastLogon(String username);

    /**
     * 사용 가능한 그룹 목록 반환. Map<DN, 그룹명(CN)> 형태.
     */
    Map<String, String> getAvailableGroups();
}
