package com.mindone.editor.pump.dto;

import java.util.List;

/**
 * 파이썬 데이터 API(30093) {@code POST /pump-curve/manual} 요청 본문.
 *
 * <p>사용자 입력 점({@code points})을 회귀 입력으로 전달해 새 성능곡선 계수·평가지표를 받는다.
 * 응답은 자동 갱신({@code /pump-curve/auto})과 동일한 구조({@link PumpCurveAutoResult})다.</p>
 *
 * <ul>
 *   <li>{@code startDate}/{@code endDate}: 조회기간(yyyy-MM-dd 문자열).</li>
 *   <li>{@code pumpComb}: 펌프조합 문자열({@code pump_comb_m.PUMP_COMB}, 예: {@code "4,6,7,11"}).</li>
 *   <li>{@code points}: 사용자가 입력한 (유량, 양정) 점 목록.</li>
 * </ul>
 */
public record PumpCurveManualPythonRequest(
        String startDate,
        String endDate,
        String pumpComb,
        List<PumpCurvePoint> points
) {
}
