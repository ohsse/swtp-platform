package com.mindone.editor.inp.network.parser;

import java.util.Locale;

/**
 * EPANET INP 파일의 표준 섹션 종류.
 *
 * <p>각 섹션은 {@code [SECTION]} 형태의 헤더로 시작한다. 표준 섹션을 모두 열거하되, 알 수 없는
 * 섹션은 {@link #UNKNOWN} 으로 보관해 무손실로 다룬다(전 섹션 완전 파싱).</p>
 *
 * <p>EPANET 2.2 매뉴얼 기준. 가시 객체(6.4)는 {@code visual=true}, 그 외는 비가시 객체/설정(6.5)이다.</p>
 */
public enum InpSectionType {

    // ===== 가시 객체 (6.4 Editing Visual Objects) =====
    JUNCTIONS(true),
    RESERVOIRS(true),
    TANKS(true),
    PIPES(true),
    PUMPS(true),
    VALVES(true),

    // ===== 비가시 객체/설정 (6.5 Editing Nonvisual Objects) =====
    TITLE(false),
    EMITTERS(false),
    TAGS(false),
    DEMANDS(false),
    STATUS(false),
    PATTERNS(false),
    CURVES(false),
    CONTROLS(false),
    RULES(false),
    ENERGY(false),
    QUALITY(false),
    SOURCES(false),
    REACTIONS(false),
    MIXING(false),
    TIMES(false),
    REPORT(false),
    OPTIONS(false),

    // ===== 지도/표출 =====
    COORDINATES(false),
    VERTICES(false),
    LABELS(false),
    BACKDROP(false),
    END(false),

    /** 표준에 없는 미지 섹션(무손실 보관용). */
    UNKNOWN(false),
    ;

    /** 지도에 그리는 가시 객체(노드/링크) 섹션 여부. */
    private final boolean visual;

    InpSectionType(boolean visual) {
        this.visual = visual;
    }

    public boolean isVisual() {
        return visual;
    }

    /**
     * 섹션 헤더 명칭(대괄호 제외)으로 섹션 종류를 찾는다. 대소문자를 무시한다.
     *
     * @param header 헤더 명칭(예: "JUNCTIONS", "junctions")
     * @return 일치하는 섹션 종류. 없으면 {@link #UNKNOWN}
     */
    public static InpSectionType fromHeader(String header) {
        if (header == null) {
            return UNKNOWN;
        }
        String normalized = header.trim().toUpperCase(Locale.ROOT);
        for (InpSectionType type : values()) {
            if (type != UNKNOWN && type.name().equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
