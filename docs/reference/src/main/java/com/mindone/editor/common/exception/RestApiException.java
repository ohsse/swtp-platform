package com.mindone.editor.common.exception;

import com.mindone.editor.common.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 비즈니스 로직에서 발생하는 예외.
 *
 * <p>서비스 계층에서 처리 불가능한 상황을 만나면 해당하는 {@link ErrorCode} 와 함께
 * 이 예외를 던진다. {@link com.mindone.editor.common.exception.advice.RestApiAdvice} 가
 * 이를 가로채 공통 응답 형식으로 변환한다.</p>
 *
 * <p>{@link RuntimeException} 을 상속하므로 트랜잭션 기본 롤백 대상이 된다.</p>
 */
@Getter
@RequiredArgsConstructor
public class RestApiException extends RuntimeException {

    /** 발생한 에러의 코드. */
    private final ErrorCode errorCode;
}
