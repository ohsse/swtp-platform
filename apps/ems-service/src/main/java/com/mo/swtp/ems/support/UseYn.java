package com.mo.swtp.ems.support;

/**
 * 사용여부 — {@code CHARACTER(1)} 컬럼에 {@code @Enumerated(EnumType.STRING)}으로 매핑된다.
 *
 * <p><b>상수 이름이 1글자여야 한다.</b> 컬럼이 {@code CHARACTER(1)}이므로 2글자 이상이면
 * 삽입 시점에 {@code value too long for type character(1)}(22001)로 터진다.
 * 조용히 잘리지 않는다는 점은 다행이지만, 이 enum에 상수를 더할 때 반드시 확인할 것.
 *
 * <p>bpchar 패딩 문제는 없다 — 값이 선언 길이와 정확히 같아 남는 자리가 없다.
 */
public enum UseYn {

    /** 사용 */
    Y,

    /** 미사용 — 소프트 삭제를 이 값으로 표현한다. 물리 삭제 API를 두지 않는다 */
    N,
}
