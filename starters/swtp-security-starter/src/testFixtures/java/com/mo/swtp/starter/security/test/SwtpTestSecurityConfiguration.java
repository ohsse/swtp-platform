package com.mo.swtp.starter.security.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 통합 테스트에서 {@link SwtpTestJwt}가 발급한 토큰을 검증하도록 디코더를 교체한다.
 *
 * <pre>{@code
 * @Import(SwtpTestSecurityConfiguration.class)
 * class MyIntegrationTest extends AbstractIntegrationTest { ... }
 * }</pre>
 *
 * <p>사용자 정의 빈이라 Boot의 {@code @ConditionalOnMissingBean(JwtDecoder.class)} 자동구성보다
 * 우선한다 — 즉 테스트가 auth-service의 JWKS 엔드포인트를 네트워크로 호출하지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SwtpTestSecurityConfiguration {

    @Bean
    public JwtDecoder jwtDecoder() {
        return SwtpTestJwt.decoder();
    }
}
