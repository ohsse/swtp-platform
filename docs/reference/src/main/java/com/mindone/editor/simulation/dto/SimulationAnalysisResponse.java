package com.mindone.editor.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 관망해석 시뮬레이션 분석시각 조회 응답.
 *
 * <p>분석일시(analDateTime, 분 단위)를 기준으로 세 가지를 함께 반환한다.</p>
 * <ul>
 *   <li>{@code outFlow} — 정수장 송수유량. 설정된 태그({@code editor.simulation.out-flow-tag})로
 *       {@code TB_RAWDATA} 를 조회한 값(정수 반올림).</li>
 *   <li>{@code pumpComb} — 펌프조합. {@code tb_pump_comb_rst.act_comb} 문자열.</li>
 *   <li>{@code demands} — 노드별 수요량 목록.</li>
 * </ul>
 *
 * <p>각 값은 해당 분에 데이터가 없으면 {@code null}(demands 의 개별 항목은 {@code demand} 가 null)이다.</p>
 *
 * @param outFlow  정수장 송수유량(정수 반올림, 없으면 null)
 * @param pumpComb 펌프조합 문자열(없으면 null)
 * @param demands  노드별 수요량 목록
 */
@Schema(description = "관망해석 시뮬레이션 분석시각 조회 응답")
public record SimulationAnalysisResponse(
        @Schema(description = "정수장 송수유량(정수 반올림, 없으면 null)", example = "20720") Long outFlow,
        @Schema(description = "펌프조합(없으면 null)", example = "1,3") String pumpComb,
        @Schema(description = "노드별 수요량 목록") List<NodeDemand> demands
) {
}
