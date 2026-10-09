package com.mo.swtp.starter.web;

import java.util.List;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;

import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 → ApiResponse(code + data) 변환 규약.
 * 자동구성(@Bean)으로 등록된다 — 컴포넌트 스캔 대상이 아니다.
 *
 * <p>메시지는 서버가 내려주지 않는다 — 프론트가 code 기반 i18n으로 표현을 책임진다.
 * data 슬롯에는 에러 상세(validation 필드 오류, 디버깅 보조 detail)만 실린다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** validation 필드 오류 상세 — data 슬롯에 목록으로 실린다 */
    public record FieldErrorDetail(String field, String reason) {
    }

    /** 업무 예외 → ErrorCode가 지정한 HTTP 상태 + 선택적 detail (없으면 data null) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<String>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.of(e.getErrorCode().getCode(), e.getDetail()));
    }

    /** @Valid 실패 → COMMON-400 + 필드별 오류 목록 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> handleValidation(MethodArgumentNotValidException e) {
        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.of(CommonErrorCode.INVALID_REQUEST.getCode(), details));
    }

    /**
     * 비동기 요청(SSE 등) 타임아웃 — 정상 수명주기라 에러 로그 대상이 아니다.
     * 커밋된 스트림에는 어떤 본문도 쓸 수 없으므로 null(무기록)로 마감하고, 미커밋이면 503 규약 응답.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAsyncTimeout(HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus())
                .body(ApiResponse.of(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), null));
    }

    /**
     * 매핑되지 않은 경로 → COMMON-404.
     *
     * <p>이 핸들러가 없으면 아래 catch-all이 삼켜 <b>오타 URL 하나가 500 + ERROR 로그</b>가 된다.
     * 클라이언트 잘못을 서버 장애로 보고하는 셈이라 500 기준 알림이 오탐으로 울리고,
     * 진짜 장애가 소음에 묻힌다. 게다가 인증 화이트리스트로 열린 경로일수록 이 상황이 쉽게 발생한다
     * (인증 차단은 401로 끝나지만, 열린 경로는 디스패처까지 도달하기 때문).
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        log.debug("매핑되지 않은 요청: {}", e.getMessage());
        return ResponseEntity.status(CommonErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.of(CommonErrorCode.NOT_FOUND.getCode(), null));
    }

    /** 그 외 전부 → COMMON-500, 상세는 서버 로그에만 (응답 data는 null) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.of(CommonErrorCode.INTERNAL_ERROR.getCode(), null));
    }
}
