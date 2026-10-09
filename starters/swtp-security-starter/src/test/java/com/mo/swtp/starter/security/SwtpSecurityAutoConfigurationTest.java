package com.mo.swtp.starter.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.mo.swtp.common.audit.AuditorProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class,
                    // Boot 기본 리소스서버 체인 — 우리 체인이 이겨야 한다는 사실까지 함께 검증한다
                    OAuth2ResourceServerWebSecurityAutoConfiguration.class,
                    SwtpSecurityAutoConfiguration.class))
            // NimbusJwtDecoder는 지연 조회라 기동 시 이 URI로 네트워크 호출이 발생하지 않는다
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8085/jwks");

    @Test
    @DisplayName("서블릿 웹앱에서 플랫폼 SecurityFilterChain과 감사 주체 공급자를 등록한다")
    void registersFilterChainAndAuditorProvider() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(SwtpSecurityStarterMarker.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context).hasSingleBean(AuditorProvider.class);
        });
    }

    @Test
    @DisplayName("비웹 애플리케이션은 오염시키지 않는다 — 게이트웨이(WebFlux)와 배치 앱 보호")
    void backsOffOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SwtpSecurityAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(SwtpSecurityStarterMarker.class));
    }

    @Test
    @DisplayName("앱이 자기 SecurityFilterChain을 정의하면 스타터 기본 체인은 물러난다")
    void appDefinedFilterChainWins() {
        webRunner.withUserConfiguration(CustomFilterChainConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasBean("appSecurityFilterChain");
                    assertThat(context).doesNotHaveBean("swtpSecurityFilterChain");
                });
    }

    @Test
    @DisplayName("기본 모드는 none — 설정을 안 하면 검증하지 않는다")
    void defaultsToNoneMode() {
        webRunner.run(context -> assertThat(context.getBean(SwtpAuthProperties.class).getMode())
                .as("기본값은 배포 현실을 따라 none이다 — 검증이 필요한 정수장이 internal을 명시한다")
                .isEqualTo(com.mo.swtp.common.security.SwtpAuthMode.NONE));
    }

    @Test
    @DisplayName("mode=internal이면 리소스 서버를 얹는다 — 기본값 반대 방향도 잠근다")
    void internalModeAddsResourceServer() {
        webRunner.withPropertyValues("swtp.auth.mode=internal").run(context -> {
            assertThat(context.getBean(SwtpAuthProperties.class).getMode())
                    .isEqualTo(com.mo.swtp.common.security.SwtpAuthMode.INTERNAL);
            // 리소스 서버가 실제로 얹혔는지는 체인에 들어간 필터로 확인한다
            assertThat(bearerFilterPresent(context.getBean(SecurityFilterChain.class)))
                    .as("Bearer 토큰 인증 필터가 없으면 토큰 없는 요청이 그대로 통과한다")
                    .isTrue();
        });
    }

    @Test
    @DisplayName("mode=none이면 리소스 서버를 얹지 않는다 — 게이트웨이만 꺼서는 통과되지 않기 때문")
    void noneModeRemovesResourceServer() {
        webRunner.withPropertyValues("swtp.auth.mode=none").run(context -> {
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(bearerFilterPresent(context.getBean(SecurityFilterChain.class)))
                    .as("Bearer 토큰 인증 필터가 남아 있으면 토큰 없는 요청이 여전히 401이 된다")
                    .isFalse();
        });
    }

    /**
     * 체인에 Bearer 토큰 인증 필터가 있는지 확인한다.
     *
     * <p>클래스를 import하지 않고 단순명으로 보는 이유: 이 필터는 Spring Security 세대마다
     * 패키지가 옮겨 다녀서, 타입으로 잠그면 프레임워크 업그레이드에서 테스트가 먼저 깨진다.
     */
    private static boolean bearerFilterPresent(SecurityFilterChain chain) {
        return chain.getFilters().stream()
                .anyMatch(filter -> filter.getClass().getSimpleName().equals("BearerTokenAuthenticationFilter"));
    }

    @Test
    @DisplayName("public-paths는 플랫폼 기본값을 치환하지 않고 합집합이 된다")
    void publicPathsAreAddedNotReplacing() {
        webRunner.withPropertyValues("swtp.security.public-paths[0]=/api/auth/login")
                .run(context -> {
                    String[] resolved = context.getBean(SwtpSecurityProperties.class).resolvedPermitAllPaths();
                    assertThat(resolved).contains("/api/auth/login", "/actuator/**", "/v3/api-docs/**");
                });
    }

    @Test
    @DisplayName("roles 클레임을 ROLE_ 접두사 권한으로 매핑한다 — 접두사는 토큰에 넣지 않는다")
    void mapsRolesClaimToPrefixedAuthorities() {
        webRunner.run(context -> {
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                    .header("alg", "RS256")
                    .subject("u-1")
                    .claim("username", "admin")
                    .claim("roles", List.of("ADMIN", "OPERATOR"))
                    .build();

            var authentication = context.getBean(
                    org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter.class)
                    .convert(jwt);

            assertThat(authentication).isNotNull();
            // Security 7은 인증 요소(FactorGrantedAuthority[FACTOR_BEARER])를 자동으로 덧붙이므로
            // 역할 권한만 골라 검증한다 — containsExactly로 잠그면 프레임워크 업그레이드에서 깨진다
            assertThat(authentication.getAuthorities())
                    .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .contains("ROLE_ADMIN", "ROLE_OPERATOR");
        });
    }

    @Test
    @DisplayName("등록 파일(imports/spring.factories)에 오타가 없다")
    void registrationFilesAreCorrect() throws Exception {
        assertThat(classpathContents("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .anySatisfy(content ->
                        assertThat(content).contains(SwtpSecurityAutoConfiguration.class.getName()));

        assertThat(classpathContents("META-INF/spring.factories"))
                .anySatisfy(content ->
                        assertThat(content).contains(SwtpSecurityEnvironmentPostProcessor.class.getName()));
    }

    /**
     * 같은 이름의 리소스가 여러 JAR에 존재하므로(스프링 자신도 spring.factories를 가진다)
     * 첫 매치만 보면 안 된다. URL 스트림으로 읽어 JAR/디렉토리 구분 없이 전부 훑는다.
     */
    private static List<String> classpathContents(String resourceName) throws Exception {
        List<String> contents = new ArrayList<>();
        var urls = SwtpSecurityAutoConfigurationTest.class.getClassLoader().getResources(resourceName);
        while (urls.hasMoreElements()) {
            try (var stream = urls.nextElement().openStream()) {
                contents.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class CustomFilterChainConfiguration {

        @org.springframework.context.annotation.Bean
        SecurityFilterChain appSecurityFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll()).build();
        }
    }
}
