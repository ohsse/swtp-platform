package com.mindone.editor.common.exception.advice;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.ErrorCode;
import com.mindone.editor.common.response.ResponseObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 처리 핸들러.
 *
 * <p>컨트롤러 계층에서 빠져나오는 예외를 가로채 {@link ResponseObject} 형식으로 변환한다.
 * Spring MVC 가 발생시키는 표준 예외(검증 실패 등) 처리를 위해
 * {@link ResponseEntityExceptionHandler} 를 상속한다.</p>
 *
 * <ul>
 *     <li>{@link RestApiException} → 400 Bad Request (에러 코드명 응답)</li>
 *     <li>그 외 모든 예외 → 500 Internal Server Error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(basePackages = {"com.mindone.editor"})
public class RestApiAdvice extends ResponseEntityExceptionHandler {

    /**
     * 비즈니스 예외 처리.
     *
     * @param e 발생한 비즈니스 예외
     * @return 에러 코드명을 담은 400 응답
     */
    @ExceptionHandler(RestApiException.class)
    public <T> ResponseEntity<ResponseObject<T>> handleRestApiException(final RestApiException e) {
        final ErrorCode errorCode = e.getErrorCode();
        log.warn("RestApiException : {}", errorCode.name());

        // 기본 비즈니스 예외는 400 Bad Request 로 반환한다.
        // 도메인별로 다른 상태 코드가 필요해지면 errorCode 타입에 따라 분기를 추가한다.
        return ResponseEntity.badRequest()
                .body(makeErrorResponse(errorCode));
    }

    /** 에러 코드명을 응답 코드로 갖는 응답 객체 생성. */
    protected <T> ResponseObject<T> makeErrorResponse(ErrorCode errorCode) {
        return ResponseObject.<T>builder()
                .code(errorCode.name())
                .build();
    }

    /**
     * 정의되지 않은 모든 예외 처리.
     *
     * @param e 발생한 예외
     * @return 예외 클래스명을 담은 500 응답
     */
    @ExceptionHandler(Exception.class)
    public <T> ResponseEntity<ResponseObject<T>> handleException(final Exception e) {
        log.error("Internal Server Error : {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(makeErrorResponse(e));
    }

    /** 예외 클래스명을 응답 코드로 갖는 응답 객체 생성. */
    protected <T> ResponseObject<T> makeErrorResponse(Exception e) {
        return ResponseObject.<T>builder()
                .code(e.getClass().getSimpleName())
                .build();
    }
}
