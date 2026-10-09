package com.mo.swtp.starter.security;

import java.util.List;

import com.mo.swtp.common.audit.AuditorProvider;
import com.mo.swtp.common.security.SwtpAuthMode;
import com.mo.swtp.common.security.SwtpJwtClaims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서비스 공통 보안 자동구성 — 아키텍처 15.1의 swtp-security-starter 책임 구현.
 *
 * <p><b>검증 전담</b>이다. 서명키를 들고 있지 않고 {@code jwk-set-uri}로 받은 공개키로만 검증하므로,
 * 발급처를 auth-service에서 외부 IdP로 바꿔도 이 스타터는 그대로다(아키텍처 4.1 Optional 요건).
 *
 * <p>조건이 세 겹이다:
 * <ul>
 *   <li>{@code SERVLET} — 게이트웨이(WebFlux)를 오염시키지 않는다. web-starter와 같은 방어</li>
 *   <li>{@code @ConditionalOnClass} — 보안 의존성이 없는 앱에서는 통째로 비활성</li>
 *   <li>{@code before = ...} — Boot 4.1은 기본 보안 체인을 세 곳에서 등록하고
 *       셋 다 {@code @ConditionalOnDefaultWebSecurity}(= SecurityFilterChain 빈이 없을 때)로 동작한다.
 *       우리 체인이 먼저 등록되어야 셋 다 물러난다. 하나라도 빠뜨리면 permitAll 규칙이 무시된
 *       Boot 기본 체인이 살아남아 actuator까지 401이 된다</li>
 * </ul>
 */
@AutoConfiguration(before = {
        ServletWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, JwtDecoder.class })
@EnableConfigurationProperties({ SwtpSecurityProperties.class, SwtpAuthProperties.class })
@EnableMethodSecurity // @PreAuthorize 등 메서드 단위 권한 처리 (15.1 "권한 공통 처리")
public class SwtpSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SwtpSecurityAutoConfiguration.class);

    /**
     * 커스텀 {@code roles} 클레임을 Spring Security 권한으로 매핑한다.
     *
     * <p>토큰에는 접두사 없는 순수 역할명("ADMIN")을 담고, 여기서 {@code ROLE_}을 붙인다 —
     * 접두사는 Spring Security의 표현 규약일 뿐이라 토큰(= 외부 계약)에 새어나가면 안 된다.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter swtpJwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(SwtpJwtClaims.ROLES);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /** 인증/인가 실패 응답을 공통 봉투로 맞춘다 */
    @Bean
    @ConditionalOnMissingBean
    public SwtpSecurityErrorResponder swtpSecurityErrorResponder() {
        return new SwtpSecurityErrorResponder();
    }

    /**
     * 플랫폼 기본 SecurityFilterChain.
     *
     * <p>앱이 자기 {@code SecurityFilterChain} 빈을 정의하면 통째로 물러난다 —
     * 세밀한 경로별 권한은 앱 도메인 지식이 필요하므로 스타터가 끝까지 책임지지 않는다.
     *
     * <p>{@code swtp.auth.mode=none}이면 리소스 서버를 얹지 않고 전 경로를 연다.
     * 게이트웨이에서만 검증을 꺼도 소용이 없기 때문이다 — 다중 방어 구조라 여기서 401이 난다.
     * 두 곳이 같은 프로퍼티를 읽는 이유가 이것이다.
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain swtpSecurityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SwtpSecurityErrorResponder errorResponder,
            SwtpSecurityProperties properties,
            SwtpAuthProperties authProperties) throws Exception {
        // 스택 공통 규약 — 토큰 기반 stateless API라 세션도 CSRF 토큰도 쓰지 않고,
        // CORS는 게이트웨이가 중앙에서 처리한다(서비스에서 또 붙이면 헤더가 중복돼 브라우저가 거부한다).
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(AbstractHttpConfigurer::disable);

        if (!authProperties.getMode().verifies()) {
            log.warn("""
                    ############################################################
                    swtp.auth.mode=none — 이 서비스는 토큰을 검증하지 않는다.
                    모든 경로가 인증 없이 열린다. 감사 컬럼(rgstr_id/mdf_id)은 시스템 기본값으로 기록된다.
                    인증 체계가 없는 폐쇄망 전용 설정이다. 그 외 배포라면 즉시 internal로 바꿀 것.
                    ############################################################""");
            return http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll()).build();
        }

        log.info("서비스 인증 모드={} — JWKS {}", authProperties.getMode(), authProperties.getJwksUri());
        return http
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(properties.resolvedPermitAllPaths()).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorResponder))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .build();
    }

    /**
     * 감사 컬럼({@code rgstr_id}/{@code mdf_id})에 로그인 사용자를 공급한다.
     * 영속성 스타터가 {@code AuditorAware}를 단독 소유하고 이 SPI를 런타임 조회한다.
     */
    @Bean
    @ConditionalOnMissingBean(AuditorProvider.class)
    public AuditorProvider swtpSecurityAuditorProvider() {
        return new SecurityContextAuditorProvider();
    }

    /** 자동구성 로드 여부를 확인하기 위한 마커 빈 */
    @Bean
    public SwtpSecurityStarterMarker swtpSecurityStarterMarker() {
        return new SwtpSecurityStarterMarker();
    }

    /** {@code @CurrentUser} 파라미터 리졸버 등록 — Spring MVC가 있을 때만 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class CurrentUserResolverConfiguration implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new CurrentUserArgumentResolver());
        }
    }
}
