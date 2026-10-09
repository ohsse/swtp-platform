package com.mo.swtp.starter.web;

import com.mo.swtp.common.api.CommonErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전역 예외 변환 규약 검증.
 *
 * <p>특히 "매핑 없음"을 404로 되돌리는 규칙을 못박는다 — catch-all(Exception)이 먼저 잡으면
 * 오타 URL이 500 + ERROR 로그가 되어 장애 알림이 오탐으로 울린다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("정적 리소스 미매칭(NoResourceFoundException)은 404 — 500이 아니다")
    void noResourceFoundMapsTo404() {
        // Spring 7 시그니처: (메서드, 리소스 경로, 요청 경로)
        var response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "v3/api-docs", "/v3/api-docs"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.NOT_FOUND.getCode());
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("핸들러 미매칭(NoHandlerFoundException)도 같은 404 규약을 따른다")
    void noHandlerFoundMapsTo404() {
        var response = handler.handleNotFound(
                new NoHandlerFoundException("GET", "/nope", new org.springframework.http.HttpHeaders()));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("나머지 예외는 여전히 COMMON-500이고 상세를 응답에 싣지 않는다")
    void unexpectedExceptionStaysAt500() {
        var response = handler.handleUnexpected(new IllegalStateException("DB 접속 정보 노출 금지"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        // 내부 메시지가 새어 나가면 안 된다
        assertThat(response.getBody().data()).isNull();
    }
}
