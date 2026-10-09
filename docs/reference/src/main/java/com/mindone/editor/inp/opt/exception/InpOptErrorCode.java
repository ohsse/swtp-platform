package com.mindone.editor.inp.opt.exception;

import com.mindone.editor.common.exception.error.ErrorCode;

/**
 * INP 최적화 도메인 전용 에러 코드.
 */
public enum InpOptErrorCode implements ErrorCode {

    /** 파이썬 최적화 모듈에 실행 요청을 전달하지 못함(연결 실패/타임아웃/오류 응답). */
    OPTIMIZE_REQUEST_FAILED,

    /** 요청한 최적화 이력을 찾을 수 없음. */
    OPTIMIZE_HIST_NOT_FOUND,
    ;
}
