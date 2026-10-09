package com.mo.swtp.starter.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * OpenAPI 문서 경로를 게이트웨이 라우트에 정렬한다.
 *
 * <p>게이트웨이 라우트는 {@code Path=/{서비스명}/**} + {@code RewritePath}이므로 서비스명 접두사를
 * 벗겨 넘긴다. 즉 도달 자체는 springdoc 기본값 {@code /v3/api-docs}로도 되지만, 그러면 서비스의
 * HTTP 표면이 {@code /api/{prefix}}와 루트로 갈라진다. 문서도 같은 네임스페이스에 두어
 * 집계 URL({@code /{서비스명}/api/{prefix}/v3/api-docs})과 게이트웨이 화이트리스트
 * ({@code /*-service/api/*&#47;v3/api-docs/**})가 한 규칙으로 유지되게 한다 —
 * 게이트웨이의 {@code ApiDocsRouteConsistencyTest}가 이 형태를 빌드 시점에 검증한다.
 *
 * <p>그 접두사를 8개 서비스에 손으로 적으면 라우트와 어긋나는 순간 조용히 404가 된다.
 * {@code spring.application.name}에서 {@code -service} 접미를 떼면 정확히 라우트 접두사가 되므로
 * (master-service → master) 여기서 계산해 주입한다. 접두사가 규칙에서 벗어나는 서비스는
 * {@code swtp.openapi.route-prefix}로 직접 지정하면 된다.
 *
 * <p>{@code addLast()}(최저 우선순위)이므로 앱이나 config-server가 명시한 값이 항상 이긴다.
 * springdoc의 자체 기본값은 프로퍼티 소스가 아니라 코드 상수라 이 주입이 우선한다.
 */
public class SwtpOpenApiEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "swtpOpenApiDefaults";
    static final String ROUTE_PREFIX_PROPERTY = "swtp.openapi.route-prefix";
    static final String API_DOCS_PATH_PROPERTY = "springdoc.api-docs.path";

    private static final String SERVICE_SUFFIX = "-service";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String routePrefix = resolveRoutePrefix(environment);
        if (routePrefix == null) {
            // 이름을 알 수 없으면 아무것도 하지 않는다 — 잘못된 경로를 주입하는 것보다 기본값이 낫다
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(ROUTE_PREFIX_PROPERTY, routePrefix);
        defaults.put(API_DOCS_PATH_PROPERTY, "/api/" + routePrefix + "/v3/api-docs");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * 라우트 접두사 결정 — 명시값 우선, 없으면 애플리케이션 이름에서 유도한다.
     *
     * <p>이 EPP는 Ordered를 구현하지 않아 최저 우선순위로 실행된다. ConfigData 단계(application.yml,
     * config-server) 이후이므로 {@code spring.application.name}을 읽을 수 있다.
     */
    static String resolveRoutePrefix(ConfigurableEnvironment environment) {
        String explicit = environment.getProperty(ROUTE_PREFIX_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return explicit.strip();
        }

        String applicationName = environment.getProperty("spring.application.name");
        if (applicationName == null || applicationName.isBlank()) {
            return null;
        }

        String name = applicationName.strip();
        return name.endsWith(SERVICE_SUFFIX)
                ? name.substring(0, name.length() - SERVICE_SUFFIX.length())
                : name;
    }
}
