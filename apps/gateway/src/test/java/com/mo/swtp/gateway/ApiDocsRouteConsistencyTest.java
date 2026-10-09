package com.mo.swtp.gateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문서 집계 URL ↔ 라우트 정합성 + 라우트의 환경 독립성 검증.
 *
 * <p>이 둘이 어긋나도 앱은 정상 기동한다. 증상은 "스웨거 드롭다운에서 서비스를 골랐는데 화면이
 * 비는" 형태로만 나타나고, 로그에도 404 한 줄뿐이라 원인을 찾기 어렵다. 배선이 아니라 설정 파일
 * 안의 약속이라 컴파일러가 잡아주지 못하니 테스트로 못박는다.
 *
 * <p>과거에는 {@code config-repo/gateway-docker.yml}이 라우트 리스트를 <b>인덱스 단위로 통째
 * 치환</b>해서 "로컬은 멀쩡한데 컨테이너에서만 라우팅이 사라지는" 조합이 성립했다. 라우트 uri를
 * {@code lb://}로 바꾸면서 그 복제본 자체를 없앴고, 여기서는 복제본이 되살아나지 않도록
 * <b>uri에 호스트·포트가 박히지 않는다</b>는 사실을 대신 잠근다.
 */
class ApiDocsRouteConsistencyTest {

    /** 테스트 작업 디렉토리는 모듈 루트(apps/gateway)다 */
    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");

    private static final String ROUTES_PREFIX = "spring.cloud.gateway.server.webflux.routes";
    private static final String SWAGGER_URLS_PREFIX = "springdoc.swagger-ui.urls";

    @Test
    @DisplayName("swagger-ui.urls는 라우트 접두사와 정확히 1:1로 대응한다")
    void swaggerUrlsMatchRoutes() throws Exception {
        Map<String, Object> config = load(APPLICATION_YML);

        // 라우트 id -> 문서 URL (라우트 정의에서 유도한 기대값)
        Map<String, String> expected = new TreeMap<>();
        routePathPrefixes(config).forEach((routeId, pathPrefix) ->
                expected.put(routeId, expectedApiDocsUrl(config, routeId, pathPrefix)));

        Map<String, String> declared = new TreeMap<>();
        for (int i = 0; config.containsKey(SWAGGER_URLS_PREFIX + "[" + i + "].name"); i++) {
            declared.put((String) config.get(SWAGGER_URLS_PREFIX + "[" + i + "].name"),
                    (String) config.get(SWAGGER_URLS_PREFIX + "[" + i + "].url"));
        }

        assertThat(declared)
                .as("라우트가 없는 서비스를 드롭다운에 넣거나, 라우트만 추가하고 문서 등록을 빠뜨린 경우")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("모든 라우트가 lb://{라우트 id}를 가리킨다 — 환경별 URI 복제본이 되살아나지 않게")
    void routesResolveThroughServiceRegistry() throws Exception {
        Map<String, Object> config = load(APPLICATION_YML);

        Map<String, String> uris = new TreeMap<>();
        for (int i = 0; config.containsKey(ROUTES_PREFIX + "[" + i + "].id"); i++) {
            uris.put((String) config.get(ROUTES_PREFIX + "[" + i + "].id"),
                    (String) config.get(ROUTES_PREFIX + "[" + i + "].uri"));
        }

        assertThat(uris).isNotEmpty();
        assertThat(uris).allSatisfy((routeId, uri) -> assertThat(uri)
                .as("라우트 %s에 호스트·포트가 박히면 환경마다 다른 설정 파일이 다시 필요해진다", routeId)
                // 서비스명이 라우트 id와 다르면 레지스트리 조회가 조용히 실패한다(503)
                .isEqualTo("lb://" + routeId));
    }

    @Test
    @DisplayName("문서 경로가 인증 화이트리스트로 열려 있다 — 토큰 없이 스펙을 읽을 수 있어야 한다")
    void apiDocsPathsAreWhitelisted() throws Exception {
        Map<String, Object> config = load(APPLICATION_YML);

        var whitelist = new java.util.ArrayList<String>();
        for (int i = 0; config.containsKey("swtp.gateway.security.permit-all-paths[" + i + "]"); i++) {
            whitelist.add((String) config.get("swtp.gateway.security.permit-all-paths[" + i + "]"));
        }

        assertThat(whitelist).contains(
                "/*-service/api/*/v3/api-docs/**", // 서비스별 스펙 (RewritePath로 접두사가 하나 더 붙는다)
                "/swagger-ui/**",                  // 게이트웨이가 호스팅하는 집계 UI
                "/v3/api-docs/**");                // swagger-config (드롭다운 목록의 출처)
    }

    @Test
    @DisplayName("모든 라우트가 /{서비스명}/** + RewritePath 규약을 따른다 — 규약이 둘로 갈라지지 않게")
    void allRoutesFollowServiceNamePrefixConvention() throws Exception {
        Map<String, Object> config = load(APPLICATION_YML);

        routePathPrefixes(config).forEach((routeId, pathPrefix) -> {
            assertThat(pathPrefix)
                    .as("라우트 %s의 경로 접두사는 서비스명이어야 한다", routeId)
                    .isEqualTo("/" + routeId);
            assertThat(rewritesPathPrefix(config, routeId))
                    .as("라우트 %s에 RewritePath가 없다 — 접두사가 벗겨지지 않아 서비스에서 404가 된다", routeId)
                    .isTrue();
        });
    }

    /**
     * 게이트웨이에서 본 서비스 문서 URL. 라우트 방식에 따라 두 형태다.
     *
     * <p>서비스가 스펙을 노출하는 경로는 언제나 {@code /api/{prefix}/v3/api-docs}이고
     * (web-starter의 {@code SwtpOpenApiEnvironmentPostProcessor}가 application.name에서 유도한다),
     * 게이트웨이에서 본 주소는 그 앞에 무엇이 붙느냐로 갈린다.
     *
     * <ul>
     *   <li><b>RewritePath 라우트</b>({@code /{서비스명}/**}) — 필터가 접두사를 벗겨 서비스에 넘기므로
     *       브라우저 주소는 {@code /{서비스명}} + 서비스 경로다. 레지스트리 기반 {@code lb://} 라우팅으로
     *       옮기면서 도입된 규약이다.</li>
     *   <li><b>접두사를 그대로 넘기는 라우트</b>({@code /api/{prefix}/**}) — 라우트 접두사가 곧 서비스
     *       경로의 접두사라 {@code 접두사 + /v3/api-docs}가 그대로 주소가 된다. 이전 규약이고
     *       step-18에서 전부 위 방식으로 옮겼다 — 현재 이 형태의 라우트는 없다.</li>
     * </ul>
     *
     * <p>두 형태를 한 식으로 합치지 않는다 — 합치면 어느 라우트가 어느 규약인지가 식 안에 숨는다.
     * 아래 분기는 {@code allRoutesFollowServiceNamePrefixConvention}이 규약을 잠그고 있어 현재는
     * 한쪽만 타지만, 규약을 다시 열 경우에도 유도식이 맞도록 남겨 둔다.
     */
    private String expectedApiDocsUrl(Map<String, Object> config, String routeId, String pathPrefix) {
        String serviceApiDocsPath = "/api/" + routeId.replace("-service", "") + "/v3/api-docs";
        return rewritesPathPrefix(config, routeId)
                ? pathPrefix + serviceApiDocsPath
                : pathPrefix + "/v3/api-docs";
    }

    /** 해당 라우트가 RewritePath 필터로 경로 접두사를 벗겨내는가 */
    private boolean rewritesPathPrefix(Map<String, Object> config, String routeId) {
        for (int i = 0; config.containsKey(ROUTES_PREFIX + "[" + i + "].id"); i++) {
            if (!routeId.equals(config.get(ROUTES_PREFIX + "[" + i + "].id"))) {
                continue;
            }
            for (int f = 0; config.containsKey(ROUTES_PREFIX + "[" + i + "].filters[" + f + "]"); f++) {
                if (((String) config.get(ROUTES_PREFIX + "[" + i + "].filters[" + f + "]"))
                        .startsWith("RewritePath=")) {
                    return true;
                }
            }
            return false;
        }
        throw new IllegalStateException("라우트를 찾지 못했다: " + routeId);
    }

    /** 라우트 id -> Path 술어의 접두사 (예: master-service -> /master-service) */
    private Map<String, String> routePathPrefixes(Map<String, Object> config) {
        Map<String, String> prefixes = new TreeMap<>();
        for (int i = 0; config.containsKey(ROUTES_PREFIX + "[" + i + "].id"); i++) {
            String id = (String) config.get(ROUTES_PREFIX + "[" + i + "].id");
            String predicate = (String) config.get(ROUTES_PREFIX + "[" + i + "].predicates[0]");
            assertThat(predicate).as("라우트 %s의 첫 술어는 Path여야 한다", id).startsWith("Path=");
            // "Path=/api/master/**" -> "/api/master"
            prefixes.put(id, predicate.substring("Path=".length()).replace("/**", ""));
        }
        assertThat(prefixes).as("라우트를 하나도 읽지 못했다 — 프로퍼티 경로가 바뀐 것이다").isNotEmpty();
        return prefixes;
    }

    private Map<String, Object> load(Path path) throws Exception {
        assertThat(Files.exists(path)).as("설정 파일을 찾을 수 없다: %s", path.toAbsolutePath()).isTrue();

        var sources = new YamlPropertySourceLoader().load(path.toString(), new FileSystemResource(path));
        Map<String, Object> flattened = new LinkedHashMap<>();
        for (var source : sources) {
            var enumerable = (EnumerablePropertySource<?>) source;
            for (String name : enumerable.getPropertyNames()) {
                flattened.put(name, enumerable.getProperty(name));
            }
        }
        return flattened;
    }
}
