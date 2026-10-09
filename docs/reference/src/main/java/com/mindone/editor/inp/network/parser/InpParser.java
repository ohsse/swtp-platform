package com.mindone.editor.inp.network.parser;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * INP 텍스트 라인을 섹션 단위로 구조화하는 파서(Parser 단계).
 *
 * <p>{@code [SECTION]} 헤더로 섹션을 구분하고, 각 데이터 라인을 {@link InpTokenizer} 로 토큰화해
 * {@link ParsedInp} 에 담는다. 빈 줄과 주석 전용 줄(예: 컬럼 헤더 {@code ;ID Elevation ...})은
 * 데이터 행에서 제외한다. 섹션별 특수 처리:</p>
 * <ul>
 *     <li>{@code [TITLE]}: 자유 텍스트 — 토큰화 없이 라인 전체를 한 행으로 보존한다.</li>
 *     <li>{@code [RULES]}: 다중 라인 블록 — 행은 평면 보관하고, {@link ParsedInp#ruleBlocks()} 가 블록으로 묶는다.</li>
 *     <li>미지 섹션: 원본 헤더명으로 별도 보관(무손실).</li>
 * </ul>
 */
@Component
public class InpParser {

    /**
     * INP 라인 목록을 파싱한다.
     *
     * @param lines 디코딩된 INP 라인 목록
     * @return 섹션별 구조화 결과
     */
    public ParsedInp parse(List<String> lines) {
        ParsedInp result = new ParsedInp();
        InpSectionType currentType = null;     // 현재 표준 섹션(미지 섹션이면 null)
        String currentUnknownHeader = null;    // 현재 미지 섹션 헤더명
        String pendingCurveComment = null;     // CURVES 곡선 직전 주석(다음 데이터 행에 연결)
        String pendingPatternComment = null;   // PATTERNS 패턴 직전 주석(다음 데이터 행에 연결)

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNo = i + 1;
            String trimmed = raw.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            // 섹션 헤더: [SECTION]
            if (trimmed.charAt(0) == '[') {
                String header = parseHeader(trimmed);
                if (header == null) {
                    continue; // 닫는 대괄호 없는 비정상 라인은 무시
                }
                currentType = InpSectionType.fromHeader(header);
                pendingCurveComment = null;   // 섹션이 바뀌면 보류 주석 초기화
                pendingPatternComment = null;
                if (currentType == InpSectionType.UNKNOWN) {
                    currentUnknownHeader = ParsedInp.normalizeHeader(header);
                } else {
                    currentUnknownHeader = null;
                    result.ensureSection(currentType);
                }
                continue;
            }

            // 섹션 시작 전의 선행 주석/잡음은 무시
            if (currentType == null && currentUnknownHeader == null) {
                continue;
            }

            // TITLE: 자유 텍스트 라인을 그대로 보존
            if (currentType == InpSectionType.TITLE) {
                result.addRow(InpSectionType.TITLE, new SectionRow(List.of(trimmed), null, raw, lineNo));
                continue;
            }

            // 일반 데이터 라인: 토큰화 후 토큰이 없으면(주석 전용 줄) 데이터에서 제외
            SectionRow row = InpTokenizer.tokenize(raw, lineNo);
            if (row.tokens().isEmpty()) {
                // 주석 전용 줄: CURVES/PATTERNS 의 타입·설명 주석을 다음 데이터 행에 연결하기 위해 보관
                if (currentType == InpSectionType.CURVES && row.inlineComment() != null) {
                    pendingCurveComment = row.inlineComment();
                } else if (currentType == InpSectionType.PATTERNS && row.inlineComment() != null) {
                    pendingPatternComment = row.inlineComment();
                }
                continue;
            }

            if (currentType != null) {
                result.addRow(currentType, row);
                // 곡선/패턴의 첫 데이터 행에 직전 주석을 연결한 뒤 보류 주석 소진
                if (currentType == InpSectionType.CURVES && pendingCurveComment != null) {
                    result.recordCurveComment(row.id(), pendingCurveComment);
                    pendingCurveComment = null;
                } else if (currentType == InpSectionType.PATTERNS && pendingPatternComment != null) {
                    result.recordPatternComment(row.id(), pendingPatternComment);
                    pendingPatternComment = null;
                }
            } else {
                result.addUnknownRow(currentUnknownHeader, row);
            }
        }
        return result;
    }

    /** {@code [SECTION]} 형태에서 대괄호 안 헤더명을 추출한다. 닫는 대괄호가 없으면 {@code null}. */
    private String parseHeader(String trimmed) {
        int close = trimmed.indexOf(']');
        if (close <= 0) {
            return null;
        }
        return trimmed.substring(1, close).trim();
    }
}
