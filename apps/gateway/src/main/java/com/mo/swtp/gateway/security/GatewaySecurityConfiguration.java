package com.mo.swtp.gateway.security;

import com.mo.swtp.common.security.SwtpAuthMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * 게이트웨이 토큰 검증 배선.
 *
 * <p>{@code spring-boot-starter-security}가 아니라 {@code spring-security-oauth2-jose}만 의존한다 —
 * 전자를 넣으면 Boot의 반응형 시큐리티 자동구성이 켜져 <b>기본 체인이 모든 요청에 인증을 요구</b>하고,
 * 인증 판단 지점이 {@link JwtAuthGlobalFilter}와 둘로 갈린다.
 *
 * <p>{@link JwtAuthGlobalFilter}를 {@code @Component} 스캔이 아니라 여기서 {@code @Bean}으로
 * 등록하는 이유: 검증기를 만들지 말지가 {@code swtp.auth.mode}에 달려 있어서, 필터와 검증기의
 * 생성 조건이 한 화면 안에 함께 보여야 한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ GatewaySecurityProperties.class, GatewayAuthProperties.class })
public class GatewaySecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfiguration.class);

    @Bean
    public JwtAuthGlobalFilter jwtAuthGlobalFilter(GatewayAuthProperties auth,
            GatewaySecurityProperties security) {
        return new JwtAuthGlobalFilter(jwtDecoder(auth), auth.getMode(), security);
    }

    /**
     * JWKS 기반 검증기. 공개키를 원격에서 받아 캐싱하고, 토큰 헤더의 {@code kid}로 키를 고른다.
     *
     * <p>기동 시점이 아니라 첫 검증 시점에 JWKS를 조회하므로, auth-service보다 게이트웨이가
     * 먼저 떠도 기동이 막히지 않는다. 발급처 교체(외부 IdP)는 {@code swtp.auth.jwks-uri} 한 줄이다.
     *
     * <p>{@code @Bean}이 아니라 private 메서드인 이유: {@link SwtpAuthMode#NONE}에서는 검증기 자체가
     * 없어야 하는데, {@code @ConditionalOnProperty}는 "특정 값이 아닐 때"를 표현하지 못한다.
     * 조건 클래스를 새로 만드는 것보다 평범한 {@code if} 한 줄이 읽기 쉽다 —
     * 이 검증기를 필요로 하는 곳은 필터 하나뿐이라 빈으로 노출할 이유도 없다.
     *
     * @return {@code NONE} 모드에서는 {@code null}
     */
    private static ReactiveJwtDecoder jwtDecoder(GatewayAuthProperties auth) {
        if (!auth.getMode().verifies()) {
            // 설정 실수로 운영에서 켜지면 플랫폼 전체가 무인증으로 열린다.
            // 기동을 막지는 않는다 — 인증 체계가 없는 폐쇄망은 실제로 존재하는 배포 형태다.
            log.warn("""
                    ############################################################
                    swtp.auth.mode=none — 게이트웨이가 토큰을 검증하지 않는다.
                    모든 요청이 인증 없이 다운스트림으로 전달된다.
                    인증 체계가 없는 폐쇄망 전용 설정이다. 그 외 배포라면 즉시 internal로 바꿀 것.
                    ############################################################""");
            return null;
        }

        String jwksUri = auth.getJwksUri();
        if (!StringUtils.hasText(jwksUri)) {
            // 여기서 끊지 않으면 첫 요청이 들어올 때까지 정상으로 보이다가 전부 401이 된다
            throw new IllegalStateException(
                    "swtp.auth.mode=%s인데 swtp.auth.jwks-uri가 비어 있다 — 공개키 위치를 지정할 것"
                            .formatted(auth.getMode()));
        }
        log.info("게이트웨이 인증 모드={} — JWKS {}", auth.getMode(), jwksUri);
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
