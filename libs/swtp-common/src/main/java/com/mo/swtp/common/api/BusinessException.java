package com.mo.swtp.common.api;

import lombok.Getter;

/**
 * 업무 규칙 위반을 표현하는 공통 예외.
 * web-starter의 GlobalExceptionHandler가 ErrorCode의 HTTP 상태 + ApiResponse(code, detail)로 변환한다.
 *
 * <p>표현(메시지)은 프론트 i18n 책임 — detail은 디버깅 보조용 선택 정보이며 없으면 data가 null로 내려간다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 디버깅 보조 상세 (nullable) — 응답 data 슬롯에 실린다 */
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /** 상황별 상세 지정 (예: "sample-item 7 없음") */
    public BusinessException(ErrorCode errorCode, String detail) {
        // 로그 가독성: detail이 없으면 코드값을 예외 메시지로 쓴다
        super(detail != null ? detail : errorCode.getCode());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
