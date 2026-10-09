package com.mindone.editor.common.domain;

/**
 * 여부(Y/N) 값 타입.
 *
 * <p>"분석여부·표시여부·모니터링여부" 처럼 도메인 전반에서 공통으로 쓰는 여부 플래그를 표현한다.
 * DB 에는 {@code char(1)} 컬럼에 {@code 'Y'}/{@code 'N'} 으로 저장한다(엔티티에서
 * {@code @Enumerated(STRING)} + {@code @JdbcTypeCode(CHAR)} 매핑).</p>
 */
public enum YesOrNo {
    Y,
    N
}
