package com.mindone.editor.prediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예측 정확도 차트 시계열 응답(유량/압력 한 쌍).
 *
 * <p>같은 예측시간 구간(from~to)에 대해 유량태그·압력태그 각각의 시계열을 함께 반환한다.
 * 각 시계열({@link AccuracySeriesResponse})은 실측 1개 라인({@code actual}) + 예측구간별 예측 라인
 * ({@code M10}/{@code M30}/{@code H1}/{@code H3}/{@code H6})으로 구성된다.
 * 화면에서 유량 그래프와 압력 그래프를 나란히 표출하기 위한 응답이다.</p>
 *
 * @param flow     유량 시계열(유량태그 기준)
 * @param pressure 압력 시계열(압력태그 기준)
 */
@Schema(description = "예측 정확도 차트 시계열(유량/압력)")
public record AccuracySeriesPairResponse(
        @Schema(description = "유량 시계열") AccuracySeriesResponse flow,
        @Schema(description = "압력 시계열") AccuracySeriesResponse pressure
) {
}
