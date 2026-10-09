package com.mindone.editor.inp.network.compose;

import java.util.ArrayList;
import java.util.List;

/**
 * INP 파일 전체의 출력 표현 — 등장 순서가 보존된 섹션 목록(Compose 단계 산출물).
 *
 * <p>{@link com.mindone.editor.inp.network.compose.InpComposer} 가 상세조회 응답을 역변환해 만들고,
 * {@link com.mindone.editor.inp.network.writer.InpWriter} 가 텍스트/바이트로 직렬화한다. 섹션 순서는
 * EPANET/WNTR 의 표준 기재 순서를 따른다.</p>
 */
public class InpDocument {

    /** 섹션 목록(기재 순서 유지). */
    private final List<InpSection> sections = new ArrayList<>();

    /** 섹션을 추가하고 그 참조를 돌려준다(체이닝/후속 라인 추가용). */
    public InpSection addSection(String name) {
        InpSection section = InpSection.of(name);
        sections.add(section);
        return section;
    }

    /** 전체 섹션 목록(읽기용). */
    public List<InpSection> sections() {
        return sections;
    }
}
