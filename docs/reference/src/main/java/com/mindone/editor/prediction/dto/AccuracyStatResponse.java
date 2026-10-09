package com.mindone.editor.prediction.dto;

import com.mindone.editor.prediction.domain.AccuracyGrade;
import com.mindone.editor.prediction.domain.PredictionDuration;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예측구간별 정확도 통계 응답(단일 예측시간 target 의 예측구간 1행).
 *
 * <p>한 예측시간의 결과를 예측구간(horizon)별로 비교하는 시점 조회 결과다. 단일 시점이라 {@code rmse}/{@code mae}
 * 는 해당 행의 절대오차 {@code |a-p|} 와 같고(제곱오차 sqrt = 절대오차), {@code n} 은 실측이 있으면 1·없으면 0 이다.
 * {@code actualAvg} 는 실측 윈도 평균(예: M10 → target 직전 10분 평균), {@code predAvg} 는 그 시각의 예측값이다.</p>
 *
 * @param duration        예측구간 코드(M10/M30/H1/H3/H6)
 * @param durationLabel   예측구간 한글 라벨
 * @param durationMinutes 예측구간 분 단위 길이
 * @param actualAvg       실측 윈도 평균(a)
 * @param predAvg         예측값(p)
 * @param rmse            RMSE(단일 시점이라 |a-p| 와 동일)
 * @param mae             MAE(단일 시점이라 |a-p| 와 동일)
 * @param mape            오차율1 MAPE(%)
 * @param smape           오차율2 sMAPE(%)
 * @param hitRate         적중률(%) = 100 − 선택 오차율
 * @param grade           상태 등급(코드)
 * @param gradeLabel      상태 등급 한글 라벨
 * @param n               표본 수(실측 존재 시 1, 없으면 0)
 */
@Schema(description = "예측구간별 정확도 통계")
public record AccuracyStatResponse(
        @Schema(description = "예측구간 코드") PredictionDuration duration,
        @Schema(description = "예측구간 한글 라벨") String durationLabel,
        @Schema(description = "예측구간 분 단위 길이") int durationMinutes,
        @Schema(description = "실측 평균") Double actualAvg,
        @Schema(description = "예측 평균") Double predAvg,
        @Schema(description = "RMSE") Double rmse,
        @Schema(description = "MAE") Double mae,
        @Schema(description = "오차율1 MAPE(%)") Double mape,
        @Schema(description = "오차율2 sMAPE(%)") Double smape,
        @Schema(description = "적중률(%) = 100 − 선택 오차율") Double hitRate,
        @Schema(description = "상태 등급 코드") AccuracyGrade grade,
        @Schema(description = "상태 등급 한글 라벨") String gradeLabel,
        @Schema(description = "표본(평가 예측점) 수") long n
) {
}
