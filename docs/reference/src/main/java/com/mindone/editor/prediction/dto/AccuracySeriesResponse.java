package com.mindone.editor.prediction.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 차트용 시계열 응답(단일 데이터유형 = 유량 또는 압력).
 *
 * <p>화면 '예측 및 실측 비교' 그래프의 한 차트에 대응한다. {@code actual} 은 실측(계측값) 라인 1개,
 * 나머지({@code M10}/{@code M30}/{@code H1}/{@code H3}/{@code H6})는 예측구간별 예측값 라인이다.
 * 예측구간 5종은 데이터가 없어도 빈 배열로 항상 포함한다(프론트가 시리즈 누락을 신경 쓰지 않도록).</p>
 *
 * <p>실측 라인은 가장 짧은 예측구간(존재하는 것 중 최소 분)의 윈도 평균(actual_avg)을 사용한다
 * — 예측 주기(10분)와 같은 해상도라 측정 곡선에 가장 가깝다.</p>
 *
 * @param actual 실측 라인(계측값, 예측시간순)
 * @param m10    10분 예측구간 라인
 * @param m30    30분 예측구간 라인
 * @param h1     1시간 예측구간 라인
 * @param h3     3시간 예측구간 라인
 * @param h6     6시간 예측구간 라인
 */
@Schema(description = "예측 정확도 차트 시계열(단일 데이터유형)")
public record AccuracySeriesResponse(
        @Schema(description = "실측 라인(계측값)") List<ActualPoint> actual,
        @JsonProperty("M10") @Schema(description = "10분 예측구간 라인") List<PredPoint> m10,
        @JsonProperty("M30") @Schema(description = "30분 예측구간 라인") List<PredPoint> m30,
        @JsonProperty("H1") @Schema(description = "1시간 예측구간 라인") List<PredPoint> h1,
        @JsonProperty("H3") @Schema(description = "3시간 예측구간 라인") List<PredPoint> h3,
        @JsonProperty("H6") @Schema(description = "6시간 예측구간 라인") List<PredPoint> h6
) {

    /**
     * 실측 라인 한 점.
     *
     * @param predDttm 예측시간(예측 대상 시각)
     * @param value    실측 윈도 평균(계측값)
     */
    @Schema(description = "실측 라인 점")
    public record ActualPoint(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
            @Schema(description = "예측시간", example = "2026-06-26 08:00") LocalDateTime predDttm,
            @Schema(description = "계측값") Double value
    ) {
    }

    /**
     * 예측 라인 한 점.
     *
     * @param predDttm  예측시간(예측 대상 시각)
     * @param predValue 예측값
     */
    @Schema(description = "예측 라인 점")
    public record PredPoint(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
            @Schema(description = "예측시간", example = "2026-06-26 08:00") LocalDateTime predDttm,
            @Schema(description = "예측값") double predValue
    ) {
    }
}
