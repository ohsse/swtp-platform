package com.mindone.editor.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 설정 프로퍼티({@code editor.cors.*}).
 *
 * <p>이 에디터는 인증이 없는 독립 서비스로, 프론트엔드가 별도 오리진(예: 개발 서버 포트)에서
 * 브라우저로 API 를 호출한다. 어떤 오리진의 요청을 허용할지를 {@code allowed-origins} 로 지정한다.
 * 쉼표로 구분된 단일 문자열도 Spring 의 완화 바인딩으로 목록에 매핑된다(예: {@code http://a,http://b}).</p>
 *
 * <p>기본값은 전체 허용({@code *})이다. 운영 환경에서는 환경변수
 * {@code EDITOR_CORS_ALLOWED_ORIGINS} 로 특정 오리진만 지정하는 것을 권장한다. 값은
 * {@code allowedOriginPatterns} 로 적용되어 {@code *} 와 구체 오리진을 모두 표현할 수 있다.</p>
 *
 * @param allowedOrigins 허용할 오리진 패턴 목록(비어 있으면 {@code *} 로 간주)
 */
@ConfigurationProperties(prefix = "editor.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        // 미지정/공백이면 전체 허용으로 기본값 보정한다.
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("*");
        }
    }
}
