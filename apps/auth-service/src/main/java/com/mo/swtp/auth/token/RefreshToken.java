package com.mo.swtp.auth.token;

import java.time.LocalDateTime;

import com.mo.swtp.starter.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰. 폐기(revoke)라는 수정이 발생하므로 {@link BaseEntity}를 상속한다.
 *
 * <p>원문을 저장하지 않고 SHA-256 해시만 둔다 — DB가 유출돼도 그 값으로는 토큰을 재사용할 수 없다.
 * (액세스 토큰과 달리 리프레시 토큰은 서명이 아니라 저장소 조회로 검증하므로 해시 비교로 충분하다)
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "expires_dttm", nullable = false)
    private LocalDateTime expiresDttm;

    /** 폐기 시각. NULL이면 유효 */
    @Column(name = "revoked_dttm")
    private LocalDateTime revokedDttm;

    public RefreshToken(String tokenHash, String userId, LocalDateTime expiresDttm) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresDttm = expiresDttm;
    }

    /** 이미 폐기된 토큰이면 무시한다 — 재폐기가 폐기 시각을 덮어쓰면 최초 폐기 시점을 잃는다 */
    public void revoke(LocalDateTime now) {
        if (revokedDttm == null) {
            revokedDttm = now;
        }
    }

    public boolean isRevoked() {
        return revokedDttm != null;
    }

    public boolean isUsable(LocalDateTime now) {
        return !isRevoked() && expiresDttm.isAfter(now);
    }
}
