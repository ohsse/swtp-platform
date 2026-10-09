package com.mindone.editor.inp.network.exception;

import com.mindone.editor.common.exception.error.ErrorCode;

/**
 * INP 네트워크(상세조회) 도메인 전용 에러 코드.
 */
public enum InpNetworkErrorCode implements ErrorCode {

    /** 저장된 INP 물리 파일을 읽지 못함. */
    FILE_READ_ERROR,
    /** INP 파싱/결합 중 처리 실패. */
    PARSE_FAILED,
    /** 파싱 결과에 네트워크 데이터가 전혀 없음(빈 INP). */
    NETWORK_EMPTY,
    ;
}
