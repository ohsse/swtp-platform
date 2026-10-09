package com.mindone.editor.inp.network.compose;

import java.util.ArrayList;
import java.util.List;

/**
 * INP 한 섹션의 출력 표현 — 섹션명 + 데이터 라인 목록(Compose 단계 산출물).
 *
 * <p>{@code name} 은 대괄호를 제외한 표준 섹션명(예: {@code "JUNCTIONS"})이다. 라인이 비어 있어도
 * 섹션 자체는 출력되어({@code [JUNCTIONS]} + 컬럼 주석) EPANET/WNTR 의 빈 섹션 표기와 동일하게 보존된다.</p>
 *
 * @param name  대괄호 제외 섹션명
 * @param lines 섹션 데이터 라인(없으면 빈 목록)
 */
public record InpSection(String name, List<InpLine> lines) {

    /** 빈 섹션을 만든다(라인은 이후 {@link #add} 로 채운다). */
    public static InpSection of(String name) {
        return new InpSection(name, new ArrayList<>());
    }

    /** 토큰 라인을 추가한다. */
    public InpSection add(List<String> tokens) {
        lines.add(InpLine.of(tokens));
        return this;
    }

    /** 인라인 주석이 있는 토큰 라인을 추가한다. */
    public InpSection add(List<String> tokens, String comment) {
        lines.add(InpLine.of(tokens, comment));
        return this;
    }

    /** 원문 라인을 추가한다. */
    public InpSection addRaw(String raw) {
        lines.add(InpLine.raw(raw));
        return this;
    }

    /** 데이터 라인 존재 여부. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
