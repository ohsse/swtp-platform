package com.mo.swtp.auth.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰 발급 정책 ({@code swtp.auth.*}) — auth-service 전용.
 *
 * <p>검증 측(security-starter, 게이트웨이)은 이 값을 알 필요가 없다. 유효기간은 토큰의
 * {@code exp} 클레임에 실려 나가므로, 정책 변경이 검증 측 재배포를 부르지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties("swtp.auth")
public class AuthProperties {

    /** 토큰 {@code iss} 클레임 */
    private String issuer = "swtp-auth";

    /**
     * 액세스 토큰 유효기간. 짧게 잡는다 — 폐기 목록을 두지 않는 서명 검증 방식이라
     * 한 번 발급한 액세스 토큰은 만료 전까지 되돌릴 수 없다. 즉시 차단이 필요한 상황은
     * 리프레시 토큰 폐기 + 짧은 만료로 커버한다.
     */
    private Duration accessTokenValidity = Duration.ofMinutes(30);

    /** 리프레시 토큰 유효기간 — 저장소 조회 방식이라 언제든 폐기 가능하다 */
    private Duration refreshTokenValidity = Duration.ofDays(14);

    /** WebSocket/SSE 핸드셰이크 티켓 유효기간 — 쿼리 파라미터로 노출되므로 매우 짧게 */
    private Duration wsTicketValidity = Duration.ofSeconds(30);
}
