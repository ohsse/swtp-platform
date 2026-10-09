package com.mindone.editor.pump.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 파이썬 데이터 API(30093) {@code GET /pump-curve/auto/{start}/{end}/{pump_comb}} 응답 매핑.
 *
 * <p>지정 기간 실측값으로 새로 회귀분석한 성능곡선 계수와 평가지표를 돌려준다. 회귀식은 코드베이스 공통 규약
 * {@code H(Q) = a·Q² + b·Q + c} 를 따르며, 레거시 컬럼명과 다항식 항의 매핑은 {@link PumpCurveCoef} 와 동일하다.</p>
 *
 * <ul>
 *   <li>{@code pAddVal}     = {@code P_ADD_VAL}      → 2차항 계수 a</li>
 *   <li>{@code pMulVal}     = {@code P_MUL_VAL}      → 1차항 계수 b</li>
 *   <li>{@code pSqrtMulVal} = {@code P_SQRT_MUL_VAL} → 상수항 c</li>
 * </ul>
 *
 * <p><b>계수/상수는 {@link BigDecimal}</b>: 회귀식 계수·상수는 매우 작은 값(예: 2차항 {@code a}≈1e-8)이라
 * {@code double} 로 받으면 유효자릿수가 잘린다. 파이썬이 정밀도 보존을 위해 계수/상수를 <b>문자열</b>로 내려주며
 * (예: {@code "P_ADD_VAL": "120.0000001"}), 자바는 이를 {@link BigDecimal} 로 받아 원값을 보존한다.
 * Jackson 은 JSON 문자열을 {@code BigDecimal} 로 그대로 역직렬화한다.</p>
 *
 * <p>나머지 {@code avgErrorRate}(평균오차율)·{@code dataCount}(회귀 표본 수)·{@code powerUnit}(전력 원단위)·
 * {@code powerCostUnit}(전력비 원단위)는 파이썬이 산출한 평가지표로, 수치(number) 그대로 전달한다.</p>
 *
 * <p><b>null 의미</b>: 지정 기간·조합에 회귀할 실측 데이터가 부족하면 파이썬이 계수/지표를 {@code null} 로 내려준다.
 * 원시타입이면 역직렬화가 실패하므로 모든 수치 필드를 래퍼({@link BigDecimal}/{@link Double})로 받아 파싱은 성공시키고,
 * null 여부 판정(회귀 불가)은 {@link com.mindone.editor.pump.service.PumpCurveRenewService} 가 담당한다.</p>
 */
public record PumpCurveAutoResult(

        @JsonProperty("pump_combination") String pumpCombination,

        @JsonProperty("P_ADD_VAL") BigDecimal pAddVal,

        @JsonProperty("P_MUL_VAL") BigDecimal pMulVal,

        @JsonProperty("P_SQRT_MUL_VAL") BigDecimal pSqrtMulVal,

        @JsonProperty("avg_error_rate") Double avgErrorRate,

        @JsonProperty("data_count") Integer dataCount,

        @JsonProperty("power_unit") Double powerUnit,

        @JsonProperty("power_cost_unit") Double powerCostUnit
) {

    /**
     * 새 곡선·평가지표 산출에 필요한 모든 수치 필드가 채워졌는지 여부.
     *
     * <p>하나라도 {@code null} 이면 회귀가 성립하지 못한 것(실측 데이터 부족 등)으로 본다.
     * {@code dataCount}(표본 수)는 회귀 성립 여부와 무관한 부가 지표라 판정에서 제외한다.</p>
     */
    public boolean hasAllValues() {
        return pAddVal != null && pMulVal != null && pSqrtMulVal != null
                && avgErrorRate != null && powerUnit != null && powerCostUnit != null;
    }
}
