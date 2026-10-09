package com.mindone.editor.inp.vismapping.exception;

import com.mindone.editor.common.exception.error.ErrorCode;

/**
 * INP 시각화 매핑 도메인 전용 에러 코드.
 */
public enum InpVisMappingErrorCode implements ErrorCode {

    /** 요청한 매핑을 찾을 수 없음(또는 해당 INP 파일 소속이 아님). */
    MAPPING_NOT_FOUND,
    /** 같은 INP 파일에 같은 junction 의 매핑이 이미 존재함. */
    DUPLICATE_MAPPING,
    ;
}
