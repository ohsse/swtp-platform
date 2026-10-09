package com.mo.swtp.gateway.security;

import com.mo.swtp.common.security.SwtpAuthMode;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 발급처 설정 ({@code swtp.auth.*}) — 게이트웨이 쪽 바인딩.
 *
 * <p>같은 프리픽스를 security-starter의 {@code SwtpAuthProperties}도 바인딩한다. 클래스를
 * 공유하지 않는 이유는 모듈 경계다: 게이트웨이는 WebFlux라 서블릿 전용인 security-starter를
 * 의존하지 않고, 두 클래스가 함께 참조하는 {@code libs/swtp-common}은 "프레임워크 의존 최소화"가
 * 원칙이라 {@code @ConfigurationProperties}(스프링 부트 타입)를 들이지 않는다.
 * 공유해야 하는 것은 클래스가 아니라 <b>값의 해석</b>이고, 그 합의점이 {@link SwtpAuthMode}다.
 *
 * <p>{@code swtp.gateway.security.*}({@link GatewaySecurityProperties})와 프리픽스를 나눈 것도
 * 의도적이다. 이쪽은 "누가 발급한 토큰을 믿는가"(전 서비스 공통 결정),
 * 저쪽은 "어느 경로를 열어두는가"(게이트웨이 고유 관심사)다.
 */
@Getter
@Setter
@ConfigurationProperties("swtp.auth")
public class GatewayAuthProperties {

    /** 인증 검증 모드 — 미지정 시 검증하지 않는다({@link SwtpAuthMode#NONE}) */
    private SwtpAuthMode mode = SwtpAuthMode.NONE;

    /** 공개키(JWKS) 위치. {@link SwtpAuthMode#NONE}이 아니면 반드시 있어야 한다 */
    private String jwksUri;
}
