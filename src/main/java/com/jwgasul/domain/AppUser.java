// AppUser.java — 로그인 계정 엔티티(app_user 테이블). 역할 분리 없이 단일 권한(3.4)
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password; // BCrypt 해시

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String role = "ROLE_ADMIN";

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {
    }

    // 신규 계정 생성용 생성자
    public AppUser(String username, String password, String displayName, String role, boolean enabled) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
