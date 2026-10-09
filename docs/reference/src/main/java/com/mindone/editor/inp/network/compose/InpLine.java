package com.mindone.editor.inp.network.compose;

import java.util.List;

/**
 * INP 출력 한 줄의 무형식 표현(Compose 단계 산출물).
 *
 * <p>두 종류 중 하나다. 정렬·주석 형식은 {@link com.mindone.editor.inp.network.writer.InpWriter} 가 입힌다.</p>
 * <ul>
 *     <li><b>토큰 라인</b>: {@link #tokens} 가 채워진 라인. 데이터 행 한 줄을 토큰 목록 + 선택적 인라인 주석
 *         ({@link #comment})으로 담는다(예: 절점/관로/좌표 행). 토큰은 이미 문자열로 포맷된 값이다.</li>
 *     <li><b>원문 라인</b>: {@link #raw} 가 채워진 라인. 토큰화 없이 한 줄을 그대로 출력한다
 *         (예: RULES 본문, TITLE 자유 텍스트, 패턴/곡선 직전 주석).</li>
 * </ul>
 *
 * @param tokens  토큰 목록(원문 라인이면 {@code null})
 * @param comment 인라인 주석({@code ;} 뒤, 없으면 {@code null}) — 토큰 라인에서만 사용
 * @param raw     원문 한 줄(토큰 라인이면 {@code null})
 */
public record InpLine(List<String> tokens, String comment, String raw) {

    /** 토큰 라인(주석 없음). */
    public static InpLine of(List<String> tokens) {
        return new InpLine(tokens, null, null);
    }

    /** 토큰 라인(인라인 주석 포함, 주석이 {@code null} 이면 주석 없는 라인과 동일). */
    public static InpLine of(List<String> tokens, String comment) {
        return new InpLine(tokens, comment, null);
    }

    /** 원문 라인(그대로 출력). */
    public static InpLine raw(String raw) {
        return new InpLine(null, null, raw);
    }

    /** 원문 라인 여부. */
    public boolean isRaw() {
        return raw != null;
    }
}
