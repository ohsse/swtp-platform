package com.mindone.editor.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 웹 MVC 공통 설정.
 *
 * <p><b>멀티파트 JSON 파트의 octet-stream 수용</b>: 업로드 API({@code POST /inp-files})는 파일({@code file})과
 * 메타데이터 JSON({@code info}) 을 멀티파트로 함께 받는다. 이때 Swagger UI 는 객체 파트({@code info})를
 * {@code application/json} 이 아니라 {@code application/octet-stream} 으로 전송한다. 기본 설정에서는 octet-stream
 * 을 POJO 로 변환할 메시지 컨버터가 없어 {@code HttpMediaTypeNotSupportedException}(415) 이 발생한다.</p>
 *
 * <p>이를 해결하기 위해 JSON 을 다루는 메시지 컨버터(Spring Boot 4.x = Jackson 3 기반)가
 * {@code application/octet-stream} 도 읽도록 지원 미디어 타입을 확장한다. JSON 파서는 바이트 스트림을 규약에 따라
 * UTF-8 로 해석하므로 한글 메타데이터도 안전하게 보존된다. 응답(쓰기) 측 octet-stream(파일 다운로드)은 컨버터
 * 우선순위상 {@code ResourceHttpMessageConverter} 가 먼저 처리하므로 영향을 받지 않는다.</p>
 *
 * <p>컨버터 구현 클래스(Jackson 2/3)에 의존하지 않도록, {@code application/json} 을 지원하는 컨버터를
 * 동적으로 찾아 확장한다. Spring 7 의 {@link HttpMessageConverters.ServerBuilder} 기반 설정 훅을 사용한다.</p>
 *
 * <p><b>CORS</b>: 인증 없는 독립 에디터로, 프론트엔드가 별도 오리진에서 브라우저로 API 를 호출한다.
 * 허용 오리진을 {@link CorsProperties}({@code editor.cors.*}) 로 설정받아 전체 경로에 CORS 를 매핑한다.
 * 파일 다운로드 응답의 파일명을 프론트가 읽을 수 있도록 {@code Content-Disposition} 헤더를 노출한다.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 패턴 기반이라 "*" 와 구체 오리진을 모두 표현할 수 있다.
                .allowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                // 파일 다운로드 시 프론트가 파일명을 읽으려면 Content-Disposition 노출이 필요하다.
                .exposedHeaders("Content-Disposition", "Content-Type")
                // 쿠키/인증정보를 쓰지 않으므로 자격증명은 허용하지 않는다(전체 허용과 양립).
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        // 기본 컨버터를 포함한 모든 컨버터에 대해 JSON 컨버터면 octet-stream 지원을 추가한다.
        builder.configureMessageConverters(converter -> {
            if (converter instanceof AbstractHttpMessageConverter<?> jsonConverter && supportsJson(jsonConverter)) {
                addOctetStreamSupport(jsonConverter);
            }
        });
    }

    /** 컨버터가 {@code application/json} 을 지원하는지(= JSON 컨버터인지) 판별한다. */
    private boolean supportsJson(AbstractHttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().stream()
                .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /** JSON 컨버터의 지원 미디어 타입에 octet-stream 을 추가한다(이미 있으면 그대로 둔다). */
    private void addOctetStreamSupport(AbstractHttpMessageConverter<?> converter) {
        List<MediaType> supported = converter.getSupportedMediaTypes();
        if (supported.contains(MediaType.APPLICATION_OCTET_STREAM)) {
            return;
        }
        List<MediaType> extended = new ArrayList<>(supported);
        extended.add(MediaType.APPLICATION_OCTET_STREAM);
        converter.setSupportedMediaTypes(extended);
    }
}
