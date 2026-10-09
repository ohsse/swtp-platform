package com.mindone.editor.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * springdoc(OpenAPI) 문서 설정 — Swagger UI 의 "Servers" 드롭다운 구성.
 *
 * <p>이 서비스는 nginx 리버스 프록시 뒤에서 동작한다. 사용 환경이 두 가지라 Swagger 의 "Try it out"
 * 요청을 어디로 보낼지 사용자가 고를 수 있도록 서버 목록을 명시한다.</p>
 * <ul>
 *   <li><b>프록시 경유</b>({@code editor.swagger.proxy-url}): 외부 사용자가 nginx(예: {@code :30090/api})를
 *       통해 접근하는 정상 경로.</li>
 *   <li><b>백엔드 직접</b>({@code editor.swagger.direct-url}): 프록시를 건너뛰고 백엔드 포트
 *       (예: {@code :30091/api})로 바로 접근하는 경로(디버깅/점검용).</li>
 * </ul>
 *
 * <p><b>주의</b>: 서버 URL 에는 컨텍스트 경로({@code /api})까지 포함해야 한다. OpenAPI 의 {@code paths}
 * 는 컨텍스트 경로를 제외한 {@code /inp-files/...} 로 생성되므로, 서버 base 가 {@code .../api} 여야
 * 최종 호출 주소가 {@code .../api/inp-files/...} 로 맞는다.</p>
 *
 * <p>두 프로퍼티는 환경(지자체)·포트마다 다르므로 dev 프로파일 오버레이
 * ({@code resources-env/dev/application.yaml})에서 값을 주입한다. 값이 비어 있으면(로컬 등) 해당 항목을
 * 추가하지 않으며, 둘 다 비면 springdoc 이 요청 기준으로 서버를 자동 생성한다(기존 동작 유지).</p>
 */
@Configuration
public class OpenApiConfig {

    private final String proxyUrl;
    private final String directUrl;

    public OpenApiConfig(
            @Value("${editor.swagger.proxy-url:}") String proxyUrl,
            @Value("${editor.swagger.direct-url:}") String directUrl) {
        this.proxyUrl = proxyUrl;
        this.directUrl = directUrl;
    }

    @Bean
    public OpenAPI editorOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("INP Editor API")
                        .description("EPANET 상수도 관망 모델(.inp) 업로드·파싱·표출·편집·저장 API")
                        .version("v1"));

        List<Server> servers = new ArrayList<>();
        if (StringUtils.hasText(proxyUrl)) {
            servers.add(new Server().url(proxyUrl).description("nginx 프록시 경유 (외부 접근)"));
        }
        if (StringUtils.hasText(directUrl)) {
            servers.add(new Server().url(directUrl).description("백엔드 직접 접근 (디버깅용)"));
        }
        // 서버를 명시하면 springdoc 의 자동 서버 생성을 대체한다. 둘 다 비어 있으면 자동 생성에 맡긴다.
        if (!servers.isEmpty()) {
            openAPI.servers(servers);
        }
        return openAPI;
    }
}
