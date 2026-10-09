package com.mo.swtp.starter.web;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpWebAutoConfigurationTest {

    // SERVLET 조건 충족 — 서블릿 웹 앱 컨텍스트 시뮬레이션
    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SwtpWebAutoConfiguration.class, SwtpOpenApiAutoConfiguration.class));

    @Test
    @DisplayName("서블릿 웹 앱에서 마커/예외 핸들러/OpenAPI/요청ID 필터 빈이 등록된다")
    void servletAppRegistersBeans() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(SwtpWebStarterMarker.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context).hasBean("swtpRequestIdMdcFilterRegistration");
        });
    }

    @Test
    @DisplayName("요청 ID 필터는 최우선 순위 — 예외 처리 로그까지 상관관계 ID를 갖는다")
    void requestIdFilterRunsFirst() {
        webRunner.run(context -> {
            var registration = context.getBean(
                    "swtpRequestIdMdcFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(registration.getFilter()).isInstanceOf(RequestIdMdcFilter.class);
        });
    }

    @Test
    @DisplayName("비웹 앱에서는 아무 빈도 등록되지 않는다 — gateway(WebFlux) 오염 방지")
    void nonWebAppRegistersNothing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SwtpWebAutoConfiguration.class, SwtpOpenApiAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SwtpWebStarterMarker.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(OpenAPI.class);
                    assertThat(context).doesNotHaveBean("swtpRequestIdMdcFilterRegistration");
                });
    }

    @Test
    @DisplayName("앱이 자체 핸들러/OpenAPI 빈을 정의하면 자동구성이 물러난다(backoff)")
    void userDefinedBeansBackOff() {
        webRunner.withUserConfiguration(CustomBeans.class).run(context -> {
            assertThat(context.getBean(GlobalExceptionHandler.class))
                    .isSameAs(context.getBean(CustomBeans.class).customHandler());
            assertThat(context.getBean(OpenAPI.class).getInfo().getTitle()).isEqualTo("custom");
        });
    }

    @Test
    @DisplayName("공통 OpenAPI 명세 제목은 spring.application.name을 따른다")
    void openApiTitleUsesApplicationName() {
        webRunner.withPropertyValues("spring.application.name=master-service")
                .run(context -> assertThat(context.getBean(OpenAPI.class).getInfo().getTitle())
                        .isEqualTo("master-service API"));
    }

    @Test
    @DisplayName("공통 명세는 Bearer 인증 스킴을 선언한다 — 집계 swagger-ui의 Authorize 버튼 근거")
    void openApiDeclaresBearerSecurityScheme() {
        webRunner.run(context -> {
            OpenAPI openApi = context.getBean(OpenAPI.class);

            var scheme = openApi.getComponents().getSecuritySchemes()
                    .get(SwtpOpenApiAutoConfiguration.BEARER_SCHEME);
            assertThat(scheme).isNotNull();
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(scheme.getScheme()).isEqualTo("bearer");
            assertThat(scheme.getBearerFormat()).isEqualTo("JWT");

            // 전역 요구사항이 없으면 Authorize로 토큰을 넣어도 요청에 실리지 않는다
            assertThat(openApi.getSecurity())
                    .anySatisfy(requirement -> assertThat(requirement)
                            .containsKey(SwtpOpenApiAutoConfiguration.BEARER_SCHEME));
        });
    }

    @Test
    @DisplayName("servers는 상대 URL이다 — 게이트웨이 프록시 시 컨테이너 내부 주소가 박히는 것을 막는다")
    void openApiUsesRelativeServerUrl() {
        // springdoc은 servers 미지정 시 들어온 요청의 Host로 계산한다. 게이트웨이 경유 요청에서는
        // 그 Host가 master-service:8081이라 브라우저가 도달할 수 없는 주소가 된다.
        webRunner.run(context -> assertThat(context.getBean(OpenAPI.class).getServers())
                .singleElement()
                .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/")));
    }

    @Test
    @DisplayName("AutoConfiguration.imports / spring.factories에 클래스가 모두 등록되어 있다")
    void registrationFilesContainAllClasses() throws Exception {
        // 파일의 오타(패키지/클래스명 불일치)를 조기에 잡는다
        String imports = classpathContents(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(imports).contains(SwtpWebAutoConfiguration.class.getName());
        assertThat(imports).contains(SwtpOpenApiAutoConfiguration.class.getName());

        String factories = classpathContents("META-INF/spring.factories");
        assertThat(factories).contains(SwtpOpenApiEnvironmentPostProcessor.class.getName());
    }

    /**
     * 클래스패스의 <b>모든</b> 동명 리소스를 이어붙여 읽는다.
     *
     * <p>{@code getResource()}는 첫 매치 하나만 돌려주는데, 이 두 파일명은 스프링 자신을 포함해
     * 수많은 JAR이 갖고 있다. JAR 항목이 먼저 잡히면 {@code Path.of(uri)}가
     * FileSystemNotFoundException으로 터진다 — 클래스패스 순서에 따라 오락가락하는 테스트가 된다.
     */
    private String classpathContents(String name) throws Exception {
        var contents = new StringBuilder();
        var resources = getClass().getClassLoader().getResources(name);
        while (resources.hasMoreElements()) {
            try (var stream = resources.nextElement().openStream()) {
                contents.append(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return contents.toString();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeans {
        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        @Bean
        GlobalExceptionHandler customHandler() {
            return handler;
        }

        @Bean
        OpenAPI customOpenApi() {
            return new OpenAPI().info(new io.swagger.v3.oas.models.info.Info().title("custom"));
        }
    }
}
