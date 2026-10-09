package com.mo.swtp.auth.user;

import com.mo.swtp.starter.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼 사용자. 등록·수정이 모두 발생하므로 {@link BaseEntity}를 상속한다.
 *
 * <p>스키마는 하드코딩하지 않는다 — {@code hibernate.default_schema=auth}가 결정한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** 불변 식별자 — 토큰 {@code sub}이자 다른 테이블 감사 컬럼에 기록되는 값 */
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    /** 로그인 ID — 변경 가능하므로 식별자로 쓰지 않는다 */
    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    public User(String userId, String username, String passwordHash, String displayName) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;
    }
}
