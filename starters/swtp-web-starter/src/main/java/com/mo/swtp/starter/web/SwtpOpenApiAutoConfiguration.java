package com.mo.swtp.starter.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mo.swtp.common.api.CommonErrorCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OpenApiCustomizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 공통 OpenAPI 명세 자동구성.
 *
 * <p>서비스 이름을 제목으로 하는 기본 명세에 더해 세 가지를 규약으로 고정한다.
 * <ul>
 *   <li><b>Bearer 인증 스킴</b> — 게이트웨이 집계 swagger-ui의 Authorize 버튼은 선택된 스펙이
 *       선언한 securityScheme만 보여준다. 서비스마다 선언하지 않으면 토큰 입력 자체가 불가능하다.</li>
 *   <li><b>상대 서버 URL</b> — 아래 상세 설명 참조.</li>
 *   <li><b>공통 에러 응답</b> — {@link #swtpCommonErrorResponsesCustomizer()} 참조.</li>
 * </ul>
 *
 * <p>앱이 자체 OpenAPI 빈을 정의하면 물러난다(backoff).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, OpenApiCustomizer.class})
public class SwtpOpenApiAutoConfiguration {

    /** swagger-ui Authorize 버튼이 참조하는 스킴 이름 */
    static final String BEARER_SCHEME = "bearerAuth";

    /** 공통 에러 응답 스키마 이름 — 각 operation은 이것을 {@code $ref}로 참조한다 */
    static final String ERROR_SCHEMA_NAME = "ErrorResponse";

    static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA_NAME;

    private static final String JSON = "application/json";

    /**
     * 모든 operation에 붙이는 공통 에러 응답.
     *
     * <p><b>400·401·500 셋으로 한정한다.</b> 판정 기준은 "어느 엔드포인트에서나 날 수 있는가"다.
     * 400은 {@code GlobalExceptionHandler.handleValidation}, 500은 같은 클래스의 catch-all,
     * 401은 swtp-security-starter가 낸다 — 셋 다 특정 핸들러에 매이지 않는다.
     *
     * <p>반면 404({@code NoResourceFoundException} 또는 도메인 not-found)와
     * 503({@code AsyncRequestTimeoutException})은 <b>넣지 않는다.</b> 전부에 붙이면
     * "목록 조회가 404를 낼 수 있다"는 거짓이 스펙에 실린다 — 정확하게 만들려는 작업이
     * 부정확을 생산하게 된다. 이 둘은 해당 핸들러가 {@code @ApiResponse}로 직접 명시한다.
     */
    private static final List<CommonError> COMMON_ERRORS = List.of(
            new CommonError("400", CommonErrorCode.INVALID_REQUEST,
                    "요청 형식·값 오류. 검증에 실패하면 data에 필드별 오류 목록이 실린다"),
            new CommonError("401", CommonErrorCode.UNAUTHORIZED,
                    "인증 실패 — 토큰이 없거나 만료됐거나 서명이 맞지 않는다"),
            new CommonError("500", CommonErrorCode.INTERNAL_ERROR,
                    "서버 내부 오류. 상세는 서버 로그에만 남고 응답에 실리지 않는다"));

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI swtpOpenApi(@Value("${spring.application.name:swtp}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("스마트정수장 플랫폼 — " + applicationName)
                        .version("v1"))
                // 상대 URL로 고정한다. springdoc은 servers를 명시하지 않으면 들어온 요청의 Host로
                // 계산하는데, 게이트웨이가 프록시하면 서비스가 보는 Host는 컨테이너 내부 이름
                // (master-service:8081)이라 브라우저가 절대 도달할 수 없는 주소가 스펙에 박힌다.
                // "/"는 스펙을 내려받은 오리진 기준으로 해석되므로 게이트웨이 경유(:8080)와
                // 서비스 직접 접근(:8081) 양쪽에서 모두 올바르게 동작한다.
                .servers(List.of(new Server().url("/").description("현재 오리진")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("POST /api/auth/login 으로 발급받은 access 토큰")))
                // 플랫폼 기본 정책이 "화이트리스트 외 전부 인증"이므로 전역 요구사항으로 건다.
                // 공개 엔드포인트는 컨트롤러에서 @SecurityRequirements(빈 값)로 해제한다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * 공통 에러 응답을 모든 operation에 주입하고, springdoc이 어긋뜨린 응답 목록을 바로잡는다.
     *
     * <p>에러 응답을 컨트롤러마다 {@code @ApiResponses}로 적으면 401·500이 모든 핸들러에 복제되고,
     * {@code GlobalExceptionHandler}를 고칠 때 함께 고쳐야 할 자리가 그만큼 흩어진다.
     * 핸들러가 이 스타터에 있으므로 그 문서도 여기 둔다.
     *
     * <p><b>이미 선언된 상태 코드는 건드리지 않는다.</b> 컨트롤러가 {@code @ApiResponse}로 적은 것이
     * 항상 이긴다 — 도메인 사정을 아는 쪽은 컨트롤러다.
     *
     * <p><b>{@code @ConditionalOnMissingBean}을 타입이 아니라 이름으로 건다.</b> OpenApiCustomizer는
     * 여러 개가 공존하는 것이 정상인 타입이라, 타입으로 걸면 앱이 전혀 다른 목적의 customizer를
     * 하나 정의하는 순간 공통 에러 응답이 조용히 사라진다.
     *
     * @see #restoreStolenSuccessResponse(ApiResponses)
     */
    @Bean
    @ConditionalOnMissingBean(name = "swtpCommonErrorResponsesCustomizer")
    public OpenApiCustomizer swtpCommonErrorResponsesCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSchemas(ERROR_SCHEMA_NAME, errorSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                        }
                        // 공통 에러를 붙이기 전에 바로잡는다 — 붙이고 나면 어느 것이 springdoc이
                        // 옮겨 붙인 것이고 어느 것이 우리가 넣은 것인지 구분이 흐려진다
                        restoreStolenSuccessResponse(responses);
                        for (CommonError error : COMMON_ERRORS) {
                            addIfAbsent(responses, error);
                        }
                    }));
        };
    }

    /**
     * springdoc이 성공 응답을 에러 응답 자리로 옮겨 붙인 것을 되돌린다.
     *
     * <p><b>핸들러에 {@code @ApiResponse}가 하나라도 선언되면 springdoc은 반환 타입에서 만들어 둔
     * 성공 응답(200)을 선언된 상태 코드 쪽으로 옮기고 200 자리를 비운다.</b> 그 결과 두 가지 거짓이
     * 한꺼번에 스펙에 실린다 — 404가 성공 봉투 스키마({@code ApiResponseTagResponse} 따위)를 광고하고,
     * 성공 응답은 아예 사라져 프론트가 정상 응답의 모양을 볼 수 없게 된다.
     *
     * <p>이 결함은 규약을 세운 커밋(841df51)의 기준 슬라이스에도 들어 있었다 — 스펙을 실제로 받아
     * 확인한 적이 없어 드러나지 않았다. 앱마다 {@code content}와 200을 손으로 적어 막을 수도 있지만,
     * 그러면 모든 핸들러가 규칙 두 개를 기억해야 하고 하나만 빠뜨려도 조용히 거짓이 실린다.
     * <b>공통 에러 응답의 모양을 정하는 자리가 여기이므로 교정도 여기서 한다.</b>
     *
     * <p>판정은 <b>"에러 응답인데 본문이 {@link #ERROR_SCHEMA_NAME}이 아니다"</b> 하나다.
     * 이 플랫폼에서 에러 응답은 예외 없이 그 봉투를 쓰므로, 그렇지 않은 본문은 옮겨 붙여진 것이다.
     * 되찾은 본문은 2xx가 하나도 없을 때만 200으로 복원한다 — 앱이 성공 응답을 직접 선언했다면
     * 그쪽이 옳고, 여기가 끼어들 자리가 아니다.
     */
    private void restoreStolenSuccessResponse(ApiResponses responses) {
        Content stolen = null;

        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            if (isSuccess(entry.getKey())) {
                continue;
            }
            Content content = entry.getValue().getContent();
            if (content == null || content.isEmpty() || carriesErrorSchema(content)) {
                continue;
            }
            if (stolen == null) {
                stolen = content;
            }
            entry.getValue().setContent(errorContent(null));
        }

        if (stolen == null || hasSuccess(responses)) {
            return;
        }
        // 200을 맨 앞에 놓는다 — 소비자가 먼저 보는 것은 성공 응답이고, 스펙 순서가 곧 화면 순서다
        Map<String, ApiResponse> existing = new LinkedHashMap<>(responses);
        responses.clear();
        responses.addApiResponse("200", new ApiResponse().description("OK").content(stolen));
        existing.forEach(responses::addApiResponse);
    }

    private boolean hasSuccess(ApiResponses responses) {
        return responses.keySet().stream().anyMatch(this::isSuccess);
    }

    private boolean isSuccess(String status) {
        return status != null && status.startsWith("2");
    }

    private boolean carriesErrorSchema(Content content) {
        return content.values().stream()
                .map(MediaType::getSchema)
                .anyMatch(schema -> schema != null && ERROR_SCHEMA_REF.equals(schema.get$ref()));
    }

    /** 성공 응답과 같은 2필드 봉투다 — 소비자가 성공·실패로 파싱 코드를 나눌 필요가 없다 */
    private Schema<?> errorSchema() {
        return new ObjectSchema()
                .description("공통 에러 응답. 성공 응답과 같은 code·data 2필드 봉투다")
                .addProperty("code", new StringSchema()
                        .description("에러 코드. 서버는 사람이 읽을 메시지를 내리지 않으며 이 값이 프론트 i18n 키다")
                        .example(CommonErrorCode.INVALID_REQUEST.getCode()))
                .addProperty("data", new ObjectSchema()
                        .nullable(true)
                        .description("에러 상세. 검증 실패(COMMON-400)면 field·reason 목록이, 그 밖에는 null이 온다"));
    }

    /** {@code example}은 공통 에러에만 있다 — 도메인이 선언한 코드는 그 문자열을 여기서 알 수 없다 */
    private Content errorContent(Map<String, Object> example) {
        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
        if (example != null) {
            mediaType.example(example);
        }
        return new Content().addMediaType(JSON, mediaType);
    }

    private void addIfAbsent(ApiResponses responses, CommonError error) {
        if (responses.containsKey(error.status())) {
            return;
        }
        // data에 null을 담아야 하므로 Map.of를 쓸 수 없다
        var example = new LinkedHashMap<String, Object>();
        example.put("code", error.errorCode().getCode());
        example.put("data", null);

        responses.addApiResponse(error.status(), new ApiResponse()
                .description(error.errorCode().getCode() + " — " + error.description())
                .content(errorContent(example)));
    }

    /** 주입할 공통 에러 하나 — 상태 코드, 그 자리에 실릴 코드 문자열, 설명 */
    private record CommonError(String status, CommonErrorCode errorCode, String description) {
    }
}
