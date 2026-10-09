package com.mindone.editor.inp.network.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * INP 텍스트를 섹션 단위로 구조화한 무손실 파싱 결과.
 *
 * <p>표준 섹션은 {@link InpSectionType} 별 행 목록으로, 표준에 없는 미지 섹션은 원본 헤더명 기준
 * 행 목록으로 보관한다. 모든 데이터 행을 토큰/주석/원본과 함께 담으므로 어떤 섹션도 유실되지 않는다.</p>
 */
public class ParsedInp {

    /** 표준 섹션별 데이터 행. 등장 순서를 유지할 필요는 없어 EnumMap 사용. */
    private final Map<InpSectionType, List<SectionRow>> sections = new EnumMap<>(InpSectionType.class);

    /** 미지 섹션(원본 헤더명 → 행 목록). 등장 순서 유지. */
    private final Map<String, List<SectionRow>> unknownSections = new LinkedHashMap<>();

    /**
     * CURVES 곡선 ID → 직전 주석(곡선 타입/설명).
     *
     * <p>EPANET 은 곡선 첫 행 앞에 {@code ;<타입>: <설명>}(예: {@code ;PUMP: EFFICIENCY: name}) 주석을 둔다.
     * 파서가 곡선의 첫 데이터 행 직전 주석만 보관해, 조회 시 곡선 타입/설명으로 노출한다.</p>
     */
    private final Map<String, String> curveComments = new LinkedHashMap<>();

    /**
     * PATTERNS 패턴 ID → 직전 주석(패턴 설명).
     *
     * <p>EPANET 은 패턴 첫 행 앞에 {@code ;<설명>} 주석을 둘 수 있다. 파서가 패턴의 첫 데이터 행 직전
     * 주석만 보관해, 조회 시 패턴 설명({@code description})으로 노출한다.</p>
     */
    private final Map<String, String> patternComments = new LinkedHashMap<>();

    /** 섹션에 데이터 행을 추가한다. */
    public void addRow(InpSectionType type, SectionRow row) {
        sections.computeIfAbsent(type, k -> new ArrayList<>()).add(row);
    }

    /** 미지 섹션에 데이터 행을 추가한다. */
    public void addUnknownRow(String header, SectionRow row) {
        unknownSections.computeIfAbsent(header, k -> new ArrayList<>()).add(row);
    }

    /** 곡선 ID 에 직전 주석(타입/설명)을 연결한다(곡선당 최초 1회만 보관). */
    public void recordCurveComment(String curveId, String comment) {
        if (curveId != null && comment != null) {
            curveComments.putIfAbsent(curveId, comment);
        }
    }

    /** 곡선 ID 의 직전 주석을 반환한다(없으면 {@code null}). */
    public String curveComment(String curveId) {
        return curveComments.get(curveId);
    }

    /** 패턴 ID 에 직전 주석(설명)을 연결한다(패턴당 최초 1회만 보관). */
    public void recordPatternComment(String patternId, String comment) {
        if (patternId != null && comment != null) {
            patternComments.putIfAbsent(patternId, comment);
        }
    }

    /** 패턴 ID 의 직전 주석(설명)을 반환한다(없으면 {@code null}). */
    public String patternComment(String patternId) {
        return patternComments.get(patternId);
    }

    /** 빈 섹션(헤더만 있고 데이터 없음)도 존재 사실을 기록한다. */
    public void ensureSection(InpSectionType type) {
        sections.computeIfAbsent(type, k -> new ArrayList<>());
    }

    /**
     * 섹션의 데이터 행 목록을 반환한다.
     *
     * @param type 섹션 종류
     * @return 데이터 행 목록(없으면 빈 목록)
     */
    public List<SectionRow> rows(InpSectionType type) {
        return sections.getOrDefault(type, Collections.emptyList());
    }

    /** 섹션 존재 여부(빈 섹션 포함). */
    public boolean has(InpSectionType type) {
        return sections.containsKey(type);
    }

    /** 전체 표준 섹션 맵(읽기 전용). */
    public Map<InpSectionType, List<SectionRow>> sections() {
        return Collections.unmodifiableMap(sections);
    }

    /** 미지 섹션 맵(읽기 전용). */
    public Map<String, List<SectionRow>> unknownSections() {
        return Collections.unmodifiableMap(unknownSections);
    }

    /**
     * {@code [RULES]} 행을 규칙 블록 단위로 묶어 반환한다.
     *
     * <p>첫 토큰이 {@code RULE} 인 행에서 새 블록이 시작되고, 그 사이 행들이 절(clause)이 된다.</p>
     *
     * @return 규칙 블록 목록(RULES 섹션이 없으면 빈 목록)
     */
    public List<RuleBlock> ruleBlocks() {
        List<RuleBlock> blocks = new ArrayList<>();
        String currentId = null;
        List<SectionRow> clauses = new ArrayList<>();
        for (SectionRow row : rows(InpSectionType.RULES)) {
            String head = row.id();
            if (head != null && head.equalsIgnoreCase("RULE")) {
                if (currentId != null) {
                    blocks.add(new RuleBlock(currentId, clauses));
                }
                currentId = row.token(1);
                clauses = new ArrayList<>();
            } else if (currentId != null) {
                clauses.add(row);
            }
        }
        if (currentId != null) {
            blocks.add(new RuleBlock(currentId, clauses));
        }
        return blocks;
    }

    /** 표준 섹션 + 미지 섹션을 합한 총 데이터 행 수(검증/디버깅용). */
    public int totalRowCount() {
        int total = 0;
        for (List<SectionRow> rows : sections.values()) {
            total += rows.size();
        }
        for (List<SectionRow> rows : unknownSections.values()) {
            total += rows.size();
        }
        return total;
    }

    /** 헤더명을 대문자 표준형으로 정규화한다(미지 섹션 키 일관성용). */
    public static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toUpperCase(Locale.ROOT);
    }
}
