package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 펌프조합 성능곡선 표본 조회 응답.
 *
 * <p>{@code comb_id} 로 조회한 유효 유량 구간({@code minFlow}~{@code maxFlow}, pump_comb_m 의
 * FC_MIN_VAL/FC_MAX_VAL)과 그 구간을 균등 분할해 산출한 (유량, 양정) 표본점 목록({@code data})을 함께 담는다.
 * 프론트는 {@code minFlow}/{@code maxFlow} 로 차트 축 범위를 잡고 {@code data} 로 곡선을 그린다.</p>
 *
 * @param minFlow 유량 구간 시작 (m³/h, pump_comb_m.FC_MIN_VAL)
 * @param maxFlow 유량 구간 끝 (m³/h, pump_comb_m.FC_MAX_VAL)
 * @param data    (유량, 양정) 표본점 목록
 */
@Schema(description = "펌프조합 성능곡선 표본 조회 응답 (유량 구간 + 표본점)")
public record PumpCurveResponse(

        @Schema(description = "유량 구간 시작 (m³/h, pump_comb_m.FC_MIN_VAL)", example = "18000.0")
        double minFlow,

        @Schema(description = "유량 구간 끝 (m³/h, pump_comb_m.FC_MAX_VAL)", example = "20500.0")
        double maxFlow,

        @Schema(description = "(유량, 양정) 표본점 목록")
        List<PumpCurvePoint> data
) {

    /** 유량 구간과 표본점 목록으로 응답을 만든다. */
    public static PumpCurveResponse of(double minFlow, double maxFlow, List<PumpCurvePoint> data) {
        return new PumpCurveResponse(minFlow, maxFlow, data);
    }
}
