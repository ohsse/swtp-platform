package com.mindone.editor.inp.network.model;

import com.mindone.editor.inp.network.parser.SectionRow;

import java.util.List;
import java.util.Locale;

/**
 * {@code [OPTIONS]} 에서 추출한 해석 옵션(상세조회 메타에 노출되는 핵심 값만 타입화).
 *
 * <p>전체 옵션 원본은 {@code ParsedInp} 에 보존되며, 여기서는 단위/손실식/수요모델/수질 등
 * 상세조회에서 자주 쓰는 값만 추출한다.</p>
 *
 * @param flowUnits   유량 단위({@code UNITS}, 예: CMH, LPS)
 * @param headloss    손실 계산식({@code HEADLOSS}, 예: H-W, D-W, C-M)
 * @param demandModel 수요 모델({@code DEMAND MODEL}, 예: DDA, PDA)
 * @param quality     수질 옵션({@code QUALITY} 첫 토큰, 예: NONE, CHEMICAL)
 */
public record NetworkOptions(String flowUnits, String headloss, String demandModel, String quality) {

    /**
     * {@code [OPTIONS]} 행 목록에서 핵심 옵션을 추출한다.
     *
     * @param rows OPTIONS 섹션의 데이터 행
     * @return 추출된 옵션(없는 값은 {@code null})
     */
    public static NetworkOptions from(List<SectionRow> rows) {
        String flowUnits = null;
        String headloss = null;
        String demandModel = null;
        String quality = null;

        for (SectionRow row : rows) {
            String key = row.token(0);
            if (key == null) {
                continue;
            }
            switch (key.toUpperCase(Locale.ROOT)) {
                case "UNITS" -> flowUnits = row.token(1);
                case "HEADLOSS" -> headloss = row.token(1);
                case "QUALITY" -> quality = row.token(1);
                case "DEMAND" -> {
                    // "DEMAND MODEL <val>" 또는 "DEMAND MULTIPLIER <val>"
                    if ("MODEL".equalsIgnoreCase(row.token(1))) {
                        demandModel = row.token(2);
                    }
                }
                default -> {
                    // 그 외 옵션은 메타로 노출하지 않음(원본은 ParsedInp 에 보존)
                }
            }
        }
        return new NetworkOptions(flowUnits, headloss, demandModel, quality);
    }
}
