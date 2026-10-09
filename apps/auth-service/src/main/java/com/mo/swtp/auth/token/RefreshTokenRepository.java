package com.mo.swtp.auth.token;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 탈취 정황 발견 시 해당 사용자의 유효 토큰을 일괄 폐기하기 위한 조회 */
    List<RefreshToken> findByUserIdAndRevokedDttmIsNull(String userId);
}
