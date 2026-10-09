package com.mindone.editor.pump.dto;

/**
 * {@code pump_comb_m} 한 행에서 읽어온 조합 식별/메타 정보 (성능곡선 갱신에 필요한 최소 컬럼).
 *
 * <ul>
 *   <li>{@code pumpComb}: {@code PUMP_COMB} — 펌프IDX 콤마 문자열(예: {@code "4,6,7,11"}). 파이썬 회귀 API 파라미터.</li>
 *   <li>{@code priority}: {@code PUMP_PRIORITY} — 조합 우선순위(미지정 시 {@code null}).</li>
 * </ul>
 */
public record PumpCombInfo(
        String pumpComb,
        Integer priority
) {
    /** 펌프 구성(PUMP_COMB)이 비어 곡선을 만들 수 없는 조합인지 여부. */
    public boolean isBlankComb() {
        return pumpComb == null || pumpComb.isBlank();
    }
}
