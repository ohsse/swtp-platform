package com.mindone.editor.inp.exception;

import com.mindone.editor.common.exception.error.ErrorCode;

/**
 * INP 파일 도메인 전용 에러 코드.
 */
public enum InpFileErrorCode implements ErrorCode {

    /** 업로드된 파일이 비어 있음. */
    EMPTY_FILE,
    /** 허용되지 않는 파일 확장자 (INP 아님). */
    INVALID_FILE_EXTENSION,
    /** 스토리지 파일 저장 실패. */
    FILE_UPLOAD_ERROR,
    /** 스토리지 파일 다운로드(ZIP 생성 등) 실패. */
    FILE_DOWNLOAD_ERROR,
    /** 요청한 INP 파일을 찾을 수 없음. */
    FILE_NOT_FOUND,
    /** 요청한 리비전을 찾을 수 없음. */
    REVISION_NOT_FOUND,
    ;
}
