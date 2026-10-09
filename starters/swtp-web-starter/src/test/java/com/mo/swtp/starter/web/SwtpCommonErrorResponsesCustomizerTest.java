package com.mo.swtp.starter.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통 에러 응답 주입 검증.
 *
 * <p>스타터 규약대로 {@code ApplicationContextRunner}로 쓴다 — 컨텍스트 전체를 띄우지 않는다.
 * customizer가 순수 함수라 픽스처 OpenAPI에 직접 적용해 결과를 본다.
 */
class SwtpCommonErrorResponsesCustomizerTest {

    private static final String ERROR_REF = "#/components/schemas/" + SwtpOpenApiAutoConfiguration.ERROR_SCHEMA_NAME;

    /** springdoc이 반환 타입에서 만드는 성공 봉투 스키마 — 에러 응답에 이것이 실려 있으면 옮겨 붙여진 것이다 */
    private static final String ENVELOPE_REF = "#/components/schemas/ApiResponseCtrlGrpResponse";

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwtpOpenApiAutoConfiguration.class));

    @Test
    @DisplayName("서블릿 웹 앱에서 customizer 빈이 등록된다")
    void servletAppRegistersCustomizer() {
        webRunner.run(context -> assertThat(context).hasBean("swtpCommonErrorResponsesCustomizer"));
    }

    @Test
    @DisplayName("비웹 앱에서는 등록되지 않는다 — gateway(WebFlux) 오염 방지")
    void nonWebAppRegistersNothing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SwtpOpenApiAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("swtpCommonErrorResponsesCustomizer"));
    }

    @Test
    @DisplayName("모든 operation에 400·401·500이 붙고 본문은 ErrorResponse를 참조한다")
    void injectsCommonErrorResponses() {
        webRunner.run(context -> {
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("성공")));

            customizer(context).customise(openApi);

            ApiResponses responses = firstOperation(openApi).getResponses();
            assertThat(responses).containsKeys("200", "400", "401", "500");

            // 코드 문자열이 프론트 i18n 키이므로 설명에 그대로 드러나야 한다
            assertThat(responses.get("401").getDescription()).startsWith("COMMON-401");

            assertThat(responses.get("400").getContent().get("application/json").getSchema().get$ref())
                    .isEqualTo(ERROR_REF);
        });
    }

    @Test
    @DisplayName("404·503은 주입하지 않는다 — 특정 핸들러에만 의미가 있어 전부에 붙이면 스펙이 거짓이 된다")
    void doesNotInjectHandlerSpecificCodes() {
        webRunner.run(context -> {
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("성공")));

            customizer(context).customise(openApi);

            assertThat(firstOperation(openApi).getResponses()).doesNotContainKeys("404", "503");
        });
    }

    @Test
    @DisplayName("컨트롤러가 이미 선언한 상태 코드는 덮어쓰지 않는다 — @ApiResponse가 항상 이긴다")
    void doesNotOverwriteDeclaredResponse() {
        webRunner.run(context -> {
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("400", new ApiResponse().description("도메인이 직접 적은 설명")));

            customizer(context).customise(openApi);

            assertThat(firstOperation(openApi).getResponses().get("400").getDescription())
                    .isEqualTo("도메인이 직접 적은 설명");
        });
    }

    @Test
    @DisplayName("ErrorResponse 스키마를 components에 한 번 등록한다 — operation마다 복제하지 않는다")
    void registersErrorSchemaInComponents() {
        webRunner.run(context -> {
            OpenAPI openApi = singleGetOperation(new ApiResponses());

            customizer(context).customise(openApi);

            var schema = openApi.getComponents().getSchemas()
                    .get(SwtpOpenApiAutoConfiguration.ERROR_SCHEMA_NAME);
            assertThat(schema).isNotNull();
            assertThat(schema.getProperties()).containsOnlyKeys("code", "data");
        });
    }

    @Test
    @DisplayName("paths가 없어도 터지지 않는다 — 엔드포인트가 아직 없는 스캐폴드 앱")
    void toleratesEmptySpec() {
        webRunner.run(context -> {
            OpenAPI openApi = new OpenAPI();

            customizer(context).customise(openApi);

            assertThat(openApi.getComponents().getSchemas())
                    .containsKey(SwtpOpenApiAutoConfiguration.ERROR_SCHEMA_NAME);
        });
    }


    @Test
    @DisplayName("springdoc이 200을 404 자리로 옮겨 붙였으면 되돌린다 — 성공 응답이 스펙에서 사라지지 않는다")
    void restoresSuccessResponseStolenByDeclaredError() {
        webRunner.run(context -> {
            // springdoc이 실제로 만들어 내는 모양 — 200이 없고, 404가 성공 봉투 스키마를 물고 있다
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("404", new ApiResponse()
                            .description("COMMON-404 — 해당 제어그룹ID가 없다")
                            .content(envelopeContent())));

            customizer(context).customise(openApi);

            ApiResponses responses = firstOperation(openApi).getResponses();

            // 성공 응답이 되살아났고 본문은 원래의 성공 봉투다
            assertThat(responses).containsKey("200");
            assertThat(responses.get("200").getContent().get("*/*").getSchema().get$ref())
                    .isEqualTo(ENVELOPE_REF);

            // 404는 도메인이 적은 설명을 유지하되 본문은 ErrorResponse로 바로잡힌다
            assertThat(responses.get("404").getDescription()).isEqualTo("COMMON-404 — 해당 제어그룹ID가 없다");
            assertThat(responses.get("404").getContent().get("application/json").getSchema().get$ref())
                    .isEqualTo(ERROR_REF);
        });
    }

    @Test
    @DisplayName("성공 응답이 이미 있으면 되돌리지 않는다 — 앱이 직접 선언한 쪽이 옳다")
    void doesNotRestoreWhenSuccessAlreadyPresent() {
        webRunner.run(context -> {
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("201", new ApiResponse().description("생성됨"))
                    .addApiResponse("409", new ApiResponse().description("중복").content(envelopeContent())));

            customizer(context).customise(openApi);

            ApiResponses responses = firstOperation(openApi).getResponses();
            assertThat(responses).doesNotContainKey("200");
            assertThat(responses.get("201").getDescription()).isEqualTo("생성됨");
            // 본문 교정은 그대로 한다 — 409가 성공 봉투를 광고하는 것은 어느 경우에나 거짓이다
            assertThat(responses.get("409").getContent().get("application/json").getSchema().get$ref())
                    .isEqualTo(ERROR_REF);
        });
    }

    @Test
    @DisplayName("에러 응답이 이미 ErrorResponse면 건드리지 않는다 — 옮겨 붙여진 것이 아니다")
    void leavesProperErrorResponseAlone() {
        webRunner.run(context -> {
            Content proper = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref(ERROR_REF)));
            OpenAPI openApi = singleGetOperation(new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("성공"))
                    .addApiResponse("404", new ApiResponse().description("없음").content(proper)));

            customizer(context).customise(openApi);

            ApiResponses responses = firstOperation(openApi).getResponses();
            assertThat(responses.get("404").getContent()).containsOnlyKeys("application/json");
            assertThat(responses.get("200").getDescription()).isEqualTo("성공");
        });
    }

    /** springdoc이 반환 타입에서 만들어 내는 성공 응답 본문 — 미디어 타입이 {@code *}/{@code *}다 */
    private Content envelopeContent() {
        return new Content().addMediaType("*/*",
                new MediaType().schema(new Schema<>().$ref(ENVELOPE_REF)));
    }
    private OpenApiCustomizer customizer(org.springframework.context.ApplicationContext context) {
        return (OpenApiCustomizer) context.getBean("swtpCommonErrorResponsesCustomizer");
    }

    private OpenAPI singleGetOperation(ApiResponses responses) {
        return new OpenAPI().paths(new Paths()
                .addPathItem("/api/ems/ctrl-grp", new PathItem().get(new Operation().responses(responses))));
    }

    private Operation firstOperation(OpenAPI openApi) {
        return openApi.getPaths().values().iterator().next().readOperations().get(0);
    }
}
