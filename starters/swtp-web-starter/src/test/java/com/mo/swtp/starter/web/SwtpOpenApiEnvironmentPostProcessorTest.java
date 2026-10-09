package com.mo.swtp.starter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문서 경로 유도 규칙 검증.
 *
 * <p>이 값이 게이트웨이 라우트 접두사와 어긋나면 집계 UI가 조용히 404를 받는다 —
 * 기동은 정상이고 스웨거 화면만 비므로 런타임에 알아채기 어렵다. 규칙을 단위 테스트로 못박는다.
 */
class SwtpOpenApiEnvironmentPostProcessorTest {

    private final SwtpOpenApiEnvironmentPostProcessor processor = new SwtpOpenApiEnvironmentPostProcessor();

    @Test
    @DisplayName("application.name에서 -service를 떼어 게이트웨이 라우트 접두사를 만든다")
    void derivesRoutePrefixFromApplicationName() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "master-service");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(SwtpOpenApiEnvironmentPostProcessor.ROUTE_PREFIX_PROPERTY))
                .isEqualTo("master");
        assertThat(environment.getProperty(SwtpOpenApiEnvironmentPostProcessor.API_DOCS_PATH_PROPERTY))
                .isEqualTo("/api/master/v3/api-docs");
    }

    @Test
    @DisplayName("-service 접미가 없으면 이름을 그대로 접두사로 쓴다")
    void usesApplicationNameAsIsWithoutSuffix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "gateway");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(SwtpOpenApiEnvironmentPostProcessor.API_DOCS_PATH_PROPERTY))
                .isEqualTo("/api/gateway/v3/api-docs");
    }

    @Test
    @DisplayName("route-prefix를 명시하면 이름 유도보다 우선한다 — 규칙에서 벗어난 라우트의 탈출구")
    void explicitRoutePrefixWins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "master-service")
                .withProperty(SwtpOpenApiEnvironmentPostProcessor.ROUTE_PREFIX_PROPERTY, "mst");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(SwtpOpenApiEnvironmentPostProcessor.API_DOCS_PATH_PROPERTY))
                .isEqualTo("/api/mst/v3/api-docs");
    }

    @Test
    @DisplayName("앱이 api-docs.path를 직접 지정하면 주입값이 이를 덮지 않는다 — addLast(최저 우선순위)")
    void doesNotOverrideExplicitApiDocsPath() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "master-service")
                .withProperty(SwtpOpenApiEnvironmentPostProcessor.API_DOCS_PATH_PROPERTY, "/custom/docs");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(SwtpOpenApiEnvironmentPostProcessor.API_DOCS_PATH_PROPERTY))
                .isEqualTo("/custom/docs");
    }

    @Test
    @DisplayName("application.name이 없으면 아무것도 주입하지 않는다 — 틀린 경로보다 기본값이 낫다")
    void skipsWhenApplicationNameMissing() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources()
                .contains(SwtpOpenApiEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}
