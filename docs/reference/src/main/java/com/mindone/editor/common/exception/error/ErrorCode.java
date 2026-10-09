package com.mindone.editor.common.exception.error;

/**
 * 에러 코드 공통 인터페이스.
 *
 * <p>도메인별 에러 코드는 이 인터페이스를 구현한 {@code enum} 으로 정의한다.
 * enum 이 구현하면 {@link #name()} 은 enum 상수명을 그대로 반환하므로,
 * 응답의 {@code code} 값으로 별도 가공 없이 사용할 수 있다.</p>
 *
 * @see CommonErrorCode
 */
public interface ErrorCode {

    /** 에러 코드명 (enum 구현 시 상수명 반환). */
    String name();
}
