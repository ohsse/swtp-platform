package com.mo.swtp.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전 서비스 공통 에러 코드.
 * 도메인 특화 에러는 각 서비스가 별도 enum으로 정의하고, 여기는 범용 코드만 유지한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** 요청 형식/값 오류 — validation 실패 등 */
    INVALID_REQUEST("COMMON-400", 400),

    /** 인증 실패 — 토큰 없음/만료/서명 불일치 */
    UNAUTHORIZED("COMMON-401", 401),

    /** 인증은 되었으나 권한 부족 */
    FORBIDDEN("COMMON-403", 403),

    /** 대상 리소스 없음 */
    NOT_FOUND("COMMON-404", 404),

    /** 서버 내부 오류 — 상세는 서버 로그에만 남긴다 */
    INTERNAL_ERROR("COMMON-500", 500),

    /** 일시적 처리 불가 — 비동기 요청 타임아웃 등 */
    SERVICE_UNAVAILABLE("COMMON-503", 503);

    private final String code;
    private final int httpStatus;
}
