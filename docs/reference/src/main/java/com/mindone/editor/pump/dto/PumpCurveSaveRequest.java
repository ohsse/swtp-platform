package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 펌프 성능곡선 저장(갱신) 요청.
 *
 * <p>갱신(renewal)·추출(extraction) 미리보기로 산출·검토한 성능곡선 회귀 계수와 평가지표를
 * {@code pump_comb_m} 한 행({@code combId})에 실제로 반영(UPDATE)하기 위한 요청이다. 프론트가 미리보기
 * 응답값을 그대로 본문에 담아 보내며(passthrough), BE 는 파이썬을 재호출하지 않고 받은 값을 그대로 저장한다.
 * {@code combId} 는 경로 변수로 받으므로 본문에 담지 않는다.</p>
 *
 * <p>회귀식은 {@code H(Q) = quadCoef·Q² + linearCoef·Q + constCoef} 이며, 레거시 컬럼 매핑은
 * {@code quadCoef=P_ADD_VAL}, {@code linearCoef=P_MUL_VAL}, {@code constCoef=P_SQRT_MUL_VAL} 이다
 * ({@link PumpCurveCoef} 규약과 동일).</p>
 *
 * <p><b>계수/상수는 {@link BigDecimal}</b>: 파이썬이 회귀 계수·상수를 정밀도 보존용 문자열로 내려주고
 * ({@link PumpCurveAutoResult}), 프론트가 그 값을 그대로 저장 본문에 담아 보내므로, 저장 요청도
 * {@link BigDecimal} 로 받아 API 경계에서의 유효자릿수 손실을 막는다. (DB 컬럼은 {@code DOUBLE} 이라
 * 저장 시 double 로 좁혀지지만, 계수 원천이 이미 {@code float64} 라 저장 정보 손실은 없다.)</p>
 *
 * @param quadCoef      2차항 계수 a (P_ADD_VAL)
 * @param linearCoef    1차항 계수 b (P_MUL_VAL)
 * @param constCoef     상수항 c (P_SQRT_MUL_VAL)
 * @param fcMin         유효 유량 구간 최소 (m³/h, FC_MIN_VAL)
 * @param fcMax         유효 유량 구간 최대 (m³/h, FC_MAX_VAL)
 * @param avgErrorRate  평균오차(율) (avg_error_rate)
 * @param powerUnit     전력 원단위 (power_unit)
 * @param powerCostUnit 전력비 원단위 (power_cost_unit)
 * @param priority      우선순위 (PUMP_PRIORITY, 미지정 시 null)
 * @param dataCount     데이터수 — 회귀에 쓰인 실측 표본 수 (data_count, 미지정 시 null)
 */
@Schema(description = "펌프 성능곡선 저장 요청 (회귀 계수 + 유량 구간 + 평가지표 passthrough)")
public record PumpCurveSaveRequest(

        @Schema(description = "2차항 계수 a (P_ADD_VAL)", example = "-0.00012")
        BigDecimal quadCoef,

        @Schema(description = "1차항 계수 b (P_MUL_VAL)", example = "0.85")
        BigDecimal linearCoef,

        @Schema(description = "상수항 c (P_SQRT_MUL_VAL)", example = "120.0")
        BigDecimal constCoef,

        @Schema(description = "유효 유량 구간 최소 (m³/h, FC_MIN_VAL)", example = "18000.0")
        double fcMin,

        @Schema(description = "유효 유량 구간 최대 (m³/h, FC_MAX_VAL)", example = "20500.0")
        double fcMax,

        @Schema(description = "평균오차(율) (avg_error_rate)", example = "0.3755")
        double avgErrorRate,

        @Schema(description = "전력 원단위 (power_unit)", example = "0.1796")
        double powerUnit,

        @Schema(description = "전력비 원단위 (power_cost_unit)", example = "30.5442")
        double powerCostUnit,

        @Schema(description = "우선순위 (PUMP_PRIORITY, 미지정 시 null)", example = "1")
        Integer priority,

        @Schema(description = "데이터수 — 회귀에 쓰인 실측 표본 수 (data_count, 미지정 시 null)", example = "9132")
        Integer dataCount
) {

    /** 유효 유량 구간이 정상인지(min &lt; max) 여부. */
    public boolean hasValidFlowRange() {
        return fcMin < fcMax;
    }
}
