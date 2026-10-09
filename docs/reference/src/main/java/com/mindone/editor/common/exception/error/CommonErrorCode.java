package com.mindone.editor.common.exception.error;

/**
 * 도메인에 종속되지 않는 공통 에러 코드.
 *
 * <p>특정 도메인(예: 파일, 파서, 프로젝트)에 한정된 에러는 해당 도메인 패키지에
 * 별도의 {@link ErrorCode} 구현 enum 을 정의하여 사용한다.</p>
 */
public enum CommonErrorCode implements ErrorCode {

    /** 시스템 내부 오류. */
    SYSTEM_ERROR,
    /** 잘못된 요청 파라미터. */
    INVALID_PARAMETER,
    /** 요청한 리소스를 찾을 수 없음. */
    RESOURCE_NOT_FOUND,
    ;
}
