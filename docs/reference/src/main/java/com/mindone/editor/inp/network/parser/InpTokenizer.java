package com.mindone.editor.inp.network.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * INP 라인 한 줄을 토큰 + 인라인 주석으로 분해하는 토크나이저.
 *
 * <p>규칙(EPANET 포맷):</p>
 * <ul>
 *     <li>{@code ;} 이후는 주석. 단, 큰따옴표({@code "}) 안의 {@code ;} 는 주석이 아니다(LABELS 텍스트 보호).</li>
 *     <li>토큰은 공백/탭으로 구분한다. EPANET ID 에는 공백이 없다.</li>
 *     <li>큰따옴표로 감싼 구간은 공백을 포함해 하나의 토큰으로 보존하고, 따옴표는 제거한다(LABELS 라벨 텍스트).</li>
 * </ul>
 */
public class InpTokenizer {

    private InpTokenizer() {
    }

    /**
     * 한 줄을 토큰과 주석으로 분해한다.
     *
     * @param raw    원본 라인(개행 제거)
     * @param lineNo 1-based 라인 번호
     * @return 토큰/주석/원본을 담은 {@link SectionRow}
     */
    public static SectionRow tokenize(String raw, int lineNo) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasCurrent = false;
        String inlineComment = null;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                hasCurrent = true; // 빈 따옴표("")도 토큰으로 인정
                continue;
            }
            if (c == ';' && !inQuotes) {
                // 주석 시작: 이후 전체를 주석으로 보관(앞쪽 공백 제거)
                inlineComment = raw.substring(i + 1).trim();
                break;
            }
            if (!inQuotes && (c == ' ' || c == '\t')) {
                if (hasCurrent) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasCurrent = false;
                }
                continue;
            }
            current.append(c);
            hasCurrent = true;
        }
        if (hasCurrent) {
            tokens.add(current.toString());
        }
        return new SectionRow(tokens, inlineComment, raw, lineNo);
    }
}
