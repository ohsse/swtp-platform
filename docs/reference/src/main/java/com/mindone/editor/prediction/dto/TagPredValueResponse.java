package com.mindone.editor.prediction.dto;

import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.domain.TagPredValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 태그 예측값 응답.
 *
 * @param predId          예측값 ID(대리키)
 * @param tagNo           태그번호
 * @param predDttm        예측시간(예측 대상 시각)
 * @param crtDttm         생성시간(예측 생성 시각)
 * @param duration        예측구간 코드(M10/M30/H1/H3/H6)
 * @param durationLabel   예측구간 한글 라벨(10분/30분/1시간/3시간/6시간)
 * @param durationMinutes 예측구간 분 단위 길이
 * @param predValue       예측값
 */
@Schema(description = "태그 예측값 응답")
public record TagPredValueResponse(
        @Schema(description = "예측값 ID(대리키)") Long predId,
        @Schema(description = "태그번호") String tagNo,
        @Schema(description = "예측시간(예측 대상 시각)") LocalDateTime predDttm,
        @Schema(description = "생성시간(예측 생성 시각)") LocalDateTime crtDttm,
        @Schema(description = "예측구간 코드(M10/M30/H1/H3/H6)") PredictionDuration duration,
        @Schema(description = "예측구간 한글 라벨") String durationLabel,
        @Schema(description = "예측구간 분 단위 길이") int durationMinutes,
        @Schema(description = "예측값") double predValue
) {

    /**
     * 예측값 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param value 예측값 엔티티
     */
    public static TagPredValueResponse from(TagPredValue value) {
        PredictionDuration duration = value.getDuration();
        return new TagPredValueResponse(
                value.getPredId(),
                value.getTagNo(),
                value.getPredDttm(),
                value.getCrtDttm(),
                duration,
                duration.getLabel(),
                duration.getMinutes(),
                value.getPredValue()
        );
    }
}
