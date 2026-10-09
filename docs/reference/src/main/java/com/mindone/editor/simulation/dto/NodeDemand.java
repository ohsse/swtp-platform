package com.mindone.editor.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 노드별 수요량 한 건.
 *
 * <p>표시명({@code name})은 {@code anal_node_tag_m.name}, 수요량({@code demand})은 해당 노드의
 * 태그({@code tag_no})로 {@code TB_RAWDATA} 를 조회한 계측값을 정수로 반올림한 값이다.
 * 해당 분에 계측값이 없으면 {@code demand} 는 {@code null} 이다.</p>
 *
 * @param name   노드 표시명
 * @param demand 수요량(정수 반올림, 계측 없으면 null)
 */
@Schema(description = "노드별 수요량")
public record NodeDemand(
        @Schema(description = "노드 표시명", example = "천마") String name,
        @Schema(description = "수요량(정수 반올림, 계측 없으면 null)", example = "500") Long demand
) {
}
