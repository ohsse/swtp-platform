package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 펌프 성능곡선 갱신 응답.
 *
 * <p>지정 기간({@code from}~{@code to}) 동안 한 펌프조합이 실제로 운전한 (유량, 양정) 실측 곡선과,
 * 같은 기간 실측값으로 파이썬이 새로 회귀분석한 계수로 그린 새 곡선을 함께 담는다. 프론트는 두 곡선을 겹쳐
 * 실측 대비 회귀 적합도를 보여주고, 평균오차·전력 단위·우선순위로 조합 평가를 표출한다.</p>
 *
 * <ul>
 *   <li>{@code currCurve}: 실측 곡선 — 분(分)별 (실측 유량 합, 실측 양정) 점. {@link PumpActualCurvePoint}.</li>
 *   <li>{@code newCurve}: 새 회귀 곡선 — {@code currCurve} 의 각 유량에 회귀식 {@code H(Q)=a·Q²+b·Q+c} 를 적용한
 *       (유량, 회귀 양정) 점. 유량 점은 실측과 1:1 대응한다.</li>
 *   <li>{@code pAddVal}     = {@code P_ADD_VAL}      → 2차항 계수 a — 파이썬 회귀 결과.</li>
 *   <li>{@code pMulVal}     = {@code P_MUL_VAL}      → 1차항 계수 b — 파이썬 회귀 결과.</li>
 *   <li>{@code pSqrtMulVal} = {@code P_SQRT_MUL_VAL} → 상수항 c — 파이썬 회귀 결과.</li>
 *   <li>{@code avgError}: 새 회귀의 평균오차(율) — 파이썬 {@code avg_error_rate}.</li>
 *   <li>{@code dataCount}: 회귀에 쓰인 실측 표본 수 — 파이썬 {@code data_count}.</li>
 *   <li>{@code powerUnit}: 전력 원단위 — 파이썬 {@code power_unit}.</li>
 *   <li>{@code powerCostUnit}: 전력비 원단위 — 파이썬 {@code power_cost_unit}.</li>
 *   <li>{@code priority}: 조합 우선순위 — {@code pump_comb_m.PUMP_PRIORITY}(미지정 시 {@code null}).</li>
 * </ul>
 *
 * <p><b>계수/상수는 {@link BigDecimal}</b>: 회귀식 계수·상수는 매우 작은 값(예: 2차항 {@code a}≈1e-8)이라
 * {@code double} 로 받으면 유효자릿수가 잘린다. 프론트는 이 미리보기 응답의 세 계수를 그대로 저장 요청
 * ({@link PumpCurveSaveRequest} 의 {@code quadCoef}/{@code linearCoef}/{@code constCoef})으로 넘긴다(passthrough).
 * 계수는 실측 데이터 부족 등으로 회귀가 성립하지 않으면 {@code null} 일 수 있다({@link PumpCurveAutoResult} 참조).</p>
 */
@Schema(description = "펌프 성능곡선 갱신 응답 (회귀 계수 + 실측 곡선 + 새 회귀 곡선 + 평가지표)")
public record PumpCurveRenewResponse(

        @Schema(description = "실측 곡선 (유량, 양정) 점 목록")
        List<PumpActualCurvePoint> currCurve,

        @Schema(description = "새 회귀 곡선 (유량, 회귀 양정) 점 목록 — 실측 유량에 1:1 대응")
        List<PumpCurvePoint> newCurve,

        @Schema(description = "2차항 계수 a (P_ADD_VAL)", example = "-0.00012")
        BigDecimal pAddVal,

        @Schema(description = "1차항 계수 b (P_MUL_VAL)", example = "0.85")
        BigDecimal pMulVal,

        @Schema(description = "상수항 c (P_SQRT_MUL_VAL)", example = "120.0")
        BigDecimal pSqrtMulVal,

        @Schema(description = "새 회귀의 평균오차(율) (파이썬 avg_error_rate)", example = "0.3755")
        double avgError,

        @Schema(description = "회귀에 쓰인 실측 표본 수 (파이썬 data_count)", example = "9132")
        Integer dataCount,

        @Schema(description = "전력 원단위 (파이썬 power_unit)", example = "0.1804")
        double powerUnit,

        @Schema(description = "전력비 원단위 (파이썬 power_cost_unit)", example = "30.5442")
        double powerCostUnit,

        @Schema(description = "조합 우선순위 (pump_comb_m.PUMP_PRIORITY, 미지정 시 null)", example = "1")
        Integer priority
) {

    /** 두 곡선과 평가지표로 응답을 만든다. */
    public static PumpCurveRenewResponse of(List<PumpActualCurvePoint> currCurve,
                                            List<PumpCurvePoint> newCurve,
                                            PumpCurveAutoResult auto,
                                            Integer priority) {
        return new PumpCurveRenewResponse(
                currCurve, newCurve,
                auto.pAddVal(), auto.pMulVal(), auto.pSqrtMulVal(),
                auto.avgErrorRate(), auto.dataCount(), auto.powerUnit(), auto.powerCostUnit(), priority);
    }
}
