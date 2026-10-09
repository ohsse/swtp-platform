package com.mo.swtp.auth.config;

import com.mo.swtp.auth.key.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * auth-service 고유 보안 배선 — 발급기(Encoder)와, 자기 토큰 검증용 디코더.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    /**
     * 플랫폼에서 유일한 토큰 서명기. 다른 서비스에는 이 빈이 존재하지 않는다.
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keyProvider.activeKey())));
    }

    /**
     * 자기가 발급한 토큰을 자기가 검증할 때 쓰는 디코더 (예: {@code /api/auth/me}).
     *
     * <p>security-starter의 기본 경로는 {@code jwk-set-uri}로 JWKS를 HTTP 조회하는 것이지만,
     * auth-service가 자기 자신에게 HTTP 요청을 보내는 것은 불필요한 자기참조다. 사용자 정의 빈이
     * Boot 자동구성({@code @ConditionalOnMissingBean})보다 우선하므로 이 선언만으로 대체된다.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider) throws Exception {
        return NimbusJwtDecoder.withPublicKey(keyProvider.activeKey().toRSAPublicKey()).build();
    }

    /**
     * BCrypt 고정. {@code DelegatingPasswordEncoder}를 쓰지 않는 이유는 저장 해시에
     * {@code {bcrypt}} 접두사를 요구해 시드 마이그레이션과 외부 계정 이관이 번거로워지기 때문이다.
     * 알고리즘 교체가 필요해지면 그 시점에 접두사 부여 마이그레이션과 함께 전환한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
