package com.mo.swtp.common.api;

/**
 * API 공통 응답 봉투.
 * 정상 응답과 예외 응답(RestControllerAdvice) 모두 같은 구조로 리턴한다.
 * 에러 상세(validation 필드 오류 등)는 data 슬롯에 담는다.
 *
 * @param code 처리 결과 코드 — 성공은 CODE_SUCCESS, 에러는 {@link ErrorCode#getCode()} 값 (프론트 i18n 키)
 * @param data 응답 데이터 (에러 시에는 에러 상세)
 */
public record ApiResponse<T>(String code, T data) {

    /** 성공 코드 */
    public static final String CODE_SUCCESS = "SUCCESS";

    /** 성공 응답 생성 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_SUCCESS, data);
    }

    /** 코드 지정 응답 생성 (예외 처리 등) */
    public static <T> ApiResponse<T> of(String code, T data) {
        return new ApiResponse<>(code, data);
    }

    /** 성공 여부 판별 — is-getter로 명명하면 Jackson이 응답에 success 필드를 추가하므로(2필드 규약 위반) 피한다 */
    public boolean success() {
        return CODE_SUCCESS.equals(code);
    }
}
