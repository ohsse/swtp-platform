package com.mo.swtp.starter.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.common.api.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 인증/인가 실패 응답을 {@link ApiResponse} 봉투로 맞춘다.
 *
 * <p>Spring Security의 401/403은 필터 단계에서 발생해 {@code @RestControllerAdvice}를 타지 않는다.
 * 손대지 않으면 같은 API가 상황에 따라 서로 다른 에러 포맷을 내보내게 되므로,
 * 여기서 web-starter의 전역 예외 규약(code + data 2필드)에 맞춰 직접 쓴다.
 *
 * <p>Jackson으로 직렬화하지 않고 문자열을 조립하는 이유: 응답 필드가 전부 이 클래스가 소유한
 * 상수라 외부 입력이 섞이지 않고, 스타터가 Jackson 버전(Boot 4의 Jackson 3 전환)에 묶이지 않는다.
 */
public class SwtpSecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다";
    private static final String FORBIDDEN_MESSAGE = "권한이 없습니다";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, CommonErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, CommonErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"%s\",\"data\":\"%s\"}".formatted(errorCode.getCode(), message));
    }
}
