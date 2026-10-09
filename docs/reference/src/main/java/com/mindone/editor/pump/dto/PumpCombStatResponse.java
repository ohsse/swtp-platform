package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 펌프조합별 운영 현황 / 전력 원단위 한 행 ({@code pump_comb_m} 전체 컬럼).
 *
 * <p>{@code pump_comb_m}(펌프조합 마스터)의 <b>모든 컬럼</b>을 그대로 읽어 담는다. 프론트가 운영 현황 표 외에도
 * 성능곡선(회귀 계수 + 유효 유량 구간)·평가지표 등 다른 화면을 함께 그릴 수 있도록 카탈로그 전체를 노출한다.
 * 운영건수(분)·전력원단위 등 통계값은 외부 EMS/AI 프로세스가 미리 적재한 값이며, 이 조회는 조회 시점에
 * 집계하지 않고 저장된 값을 단순 표출한다(가벼운 조회, 미적재 값은 {@code null}).</p>
 *
 * <h3>회귀 계수 명명</h3>
 * <p>성능곡선 회귀식은 {@code H(Q) = quadCoef·Q² + linearCoef·Q + constCoef} 이다. 레거시 컬럼명(add/mul/sqrt_mul)은
 * 실제 수학적 역할과 일치하지 않아, 코드베이스 공통 규약({@link PumpCurveCoef})대로 역할이 드러나는 이름으로 재명명한다:
 * {@code quadCoef=P_ADD_VAL}, {@code linearCoef=P_MUL_VAL}, {@code constCoef=P_SQRT_MUL_VAL}.</p>
 */
@Schema(description = "펌프조합별 운영 현황 (pump_comb_m 전체 컬럼)")
public record PumpCombStatResponse(

        @Schema(description = "펌프조합 ID (pump_comb_m.comb_id)", example = "12")
        Long combId,

        @Schema(description = "펌프조합 (펌프IDX를 ','로 이어붙인 문자열)", example = "4,6,7,11")
        String pumpComb,

        @Schema(description = "운영대수 (pump_comb_m.PUMP_COUNT, 가중값 가능)", example = "3.5")
        Double pumpCount,

        @Schema(description = "우선순위 (pump_comb_m.PUMP_PRIORITY, 미지정 시 null)", example = "1")
        Integer pumpPriority,

        @Schema(description = "성능곡선 2차항 계수 a (pump_comb_m.P_ADD_VAL)", example = "-0.00012")
        Double quadCoef,

        @Schema(description = "성능곡선 1차항 계수 b (pump_comb_m.P_MUL_VAL)", example = "0.85")
        Double linearCoef,

        @Schema(description = "성능곡선 상수항 c (pump_comb_m.P_SQRT_MUL_VAL)", example = "120.0")
        Double constCoef,

        @Schema(description = "유효 유량 구간 최소 (m³/h, pump_comb_m.FC_MIN_VAL)", example = "18000.0")
        Double fcMin,

        @Schema(description = "유효 유량 구간 최대 (m³/h, pump_comb_m.FC_MAX_VAL)", example = "20500.0")
        Double fcMax,

        @Schema(description = "배율및추가연산자 (pump_comb_m.CS_OP, 압력 계산식 미사용 레거시)", example = "*")
        String csOp,

        @Schema(description = "배율및제곱연산자 (pump_comb_m.SS_OP, 압력 계산식 미사용 레거시)", example = "*")
        String ssOp,

        @Schema(description = "운영건수(분) (pump_comb_m.run_minutes, 미적재 시 null)", example = "556")
        Long runMinutes,

        @Schema(description = "데이터수 — 회귀에 쓰인 실측 표본 수 (pump_comb_m.data_count, 미적재 시 null)", example = "9132")
        Long dataCount,

        @Schema(description = "평균오차(율) (pump_comb_m.avg_error_rate, 미적재 시 null)", example = "0.3755")
        Double avgErrorRate,

        @Schema(description = "전력 원단위 (pump_comb_m.power_unit, 미적재 시 null)", example = "0.1796")
        Double powerUnit,

        @Schema(description = "전력비 원단위 (pump_comb_m.power_cost_unit, 미적재 시 null)", example = "30.5442")
        Double powerCostUnit
) {
}
