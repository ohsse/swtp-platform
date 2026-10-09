package com.mo.swtp.starter.security;

import com.mo.swtp.common.security.SwtpAuthMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 발급처 설정 ({@code swtp.auth.*}) — 서비스 쪽 바인딩.
 *
 * <p>게이트웨이의 {@code GatewayAuthProperties}와 같은 프리픽스를 읽는다. 두 곳이 같은 값을
 * 받아야 인증이 일관되기 때문이고, 클래스를 공유하지 못하는 것은 게이트웨이가 WebFlux라
 * 이 서블릿 전용 스타터를 의존할 수 없어서다. 공유되는 것은 {@link SwtpAuthMode}의 해석이다.
 *
 * <p>{@code swtp.security.*}({@link SwtpSecurityProperties})와 프리픽스를 나눈 기준:
 * 이쪽은 "누가 발급한 토큰을 믿는가"(플랫폼 전체 공통 결정), 저쪽은 "어느 경로를 열어두는가"(앱별 결정)다.
 */
@ConfigurationProperties("swtp.auth")
public class SwtpAuthProperties {

    /** 인증 검증 모드 — 미지정 시 검증하지 않는다({@link SwtpAuthMode#NONE}) */
    private SwtpAuthMode mode = SwtpAuthMode.NONE;

    /**
     * 공개키(JWKS) 위치.
     *
     * <p>스프링 표준 키({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri})는
     * {@link SwtpSecurityEnvironmentPostProcessor}가 이 값에서 파생시킨다 —
     * 여기서 직접 읽지 않는 이유는 리소스 서버 배선의 소유자가 Boot 자동구성이기 때문이다.
     */
    private String jwksUri;

    public SwtpAuthMode getMode() {
        return mode;
    }

    public void setMode(SwtpAuthMode mode) {
        this.mode = mode;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }
}
