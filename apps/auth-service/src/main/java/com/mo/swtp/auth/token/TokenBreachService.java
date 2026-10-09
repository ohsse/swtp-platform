package com.mo.swtp.auth.token;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 탈취 정황에 대한 대응 — 해당 사용자의 유효한 리프레시 토큰을 모두 끊는다.
 *
 * <p><b>왜 별도 빈이자 별도 트랜잭션인가</b>: 호출자({@link TokenService#rotate})는 대응 직후
 * 401을 던진다. 같은 트랜잭션에서 폐기하면 그 예외로 롤백되어 <b>보안 조치가 함께 취소</b>된다
 * — 요청은 실패하는데 공격자의 토큰은 그대로 살아 있는, 조용하고 위험한 상태가 된다.
 * {@code REQUIRES_NEW}는 프록시를 타야 적용되므로 자기 호출이 되지 않게 빈을 분리했다.
 */
@Service
@RequiredArgsConstructor
public class TokenBreachService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllOf(String userId) {
        LocalDateTime now = LocalDateTime.now();
        // 벌크 UPDATE가 아니라 엔티티를 통해 폐기한다 — 벌크 쿼리는 JPA Auditing을 우회해
        // mdf_dttm/mdf_id가 비고, "언제 끊었는지"가 이력에서 사라진다.
        refreshTokenRepository.findByUserIdAndRevokedDttmIsNull(userId)
                .forEach(token -> token.revoke(now));
    }
}
