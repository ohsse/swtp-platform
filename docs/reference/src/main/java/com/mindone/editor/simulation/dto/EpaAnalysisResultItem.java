package com.mindone.editor.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 해석결과 한 지점(EPA 태그 위치)의 유량/압력 계측값 + 해석값.
 *
 * <p>{@code TB_EPA_TAG_INFO} 의 한 행(위치)에 대해, 유량 태그({@code FRI_TAG})와 압력 태그({@code PRI_TAG})로
 * {@code TB_RAWDATA} 를 조회한 계측값과, 그 계측값에 5% 미만 오차를 부여한 해석값(목업)을 담는다.</p>
 *
 * <p><b>계산식</b>: {@code analVal = val × (1 + errorRate/100)}, {@code errorRate ∈ (-5, 5)}(단위 %, ±5% 미만 랜덤 오차율).
 * 태그가 없거나 해당 분 계측이 없으면 해당 유량/압력의 값·해석값·오차율은 모두 {@code null} 이다.</p>
 *
 * <p><b>단위/자릿수</b>: 유량({@code actualFlow}/{@code analFlow})은 정수, 압력({@code actualPress}/{@code analPress})은
 * 소수 2자리, 오차율({@code flowErrorRate}/{@code pressErrorRate})은 소수 2자리(%)로 반올림한다.</p>
 *
 * @param name           위치명({@code LOCATION_NM})
 * @param actualFlow     유량 계측값(정수, 없으면 null)
 * @param analFlow       유량 해석값(정수, 없으면 null)
 * @param flowErrorRate  유량 오차율(%, -5~5, 없으면 null)
 * @param actualPress    압력 계측값(소수 2자리, 없으면 null)
 * @param analPress      압력 해석값(소수 2자리, 없으면 null)
 * @param pressErrorRate 압력 오차율(%, -5~5, 없으면 null)
 */
@Schema(description = "해석결과 한 지점(유량/압력 계측값 + 해석값)")
public record EpaAnalysisResultItem(
        @Schema(description = "위치명", example = "고산분기") String name,
        @Schema(description = "유량 계측값(정수, 없으면 null)", example = "1000") Long actualFlow,
        @Schema(description = "유량 해석값(정수, 없으면 null)", example = "1032") Long analFlow,
        @Schema(description = "유량 오차율(%, -5~5, 없으면 null)", example = "3.20") BigDecimal flowErrorRate,
        @Schema(description = "압력 계측값(소수 2자리, 없으면 null)", example = "2.53") BigDecimal actualPress,
        @Schema(description = "압력 해석값(소수 2자리, 없으면 null)", example = "2.60") BigDecimal analPress,
        @Schema(description = "압력 오차율(%, -5~5, 없으면 null)", example = "2.74") BigDecimal pressErrorRate
) {
}
