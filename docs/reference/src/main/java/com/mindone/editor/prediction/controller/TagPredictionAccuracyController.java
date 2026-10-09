package com.mindone.editor.prediction.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.prediction.domain.ErrorRateType;
import com.mindone.editor.prediction.dto.AccuracySeriesPairResponse;
import com.mindone.editor.prediction.dto.AccuracyStatResponse;
import com.mindone.editor.prediction.service.TagPredictionAccuracyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 태그 예측 정확도 모니터링 조회 명세.
 *
 * <p>집계 테이블 {@code tag_pred_eval_l} 을 읽어 화면의 ① 예측구간별 통계 테이블, ② 차트 시계열을 제공한다.
 * 적재(예측-실측 페어 + 오차항)는 MariaDB EVENT 가 수행하므로 본 API 는 조회만 담당한다.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "태그 예측 정확도", description = "예측 정확도 모니터링 조회(통계/시계열)")
public class TagPredictionAccuracyController extends CommonController {

    private final TagPredictionAccuracyService tagPredictionAccuracyService;

    @Operation(
            summary = "예측구간별 정확도 통계",
            description = "태그번호 + 단일 예측시간(target)에 대해 그 시각의 결과를 예측구간(M10/M30/H1/H3/H6)별로 "
                    + "표출한다. 예) 태그 3857, target 2026-06-26T08:00 → 08:00 예측값의 10분/30분/1시간/3시간/6시간 "
                    + "실측·예측·RMSE·MAE·MAPE·sMAPE·적중률·상태등급·표본수(n). 단일 시점이라 RMSE/MAE 는 절대오차 |a-p| "
                    + "와 같고, n 은 실측이 있으면 1·없으면 0 이다. 예측구간 5종은 데이터가 없어도 빈 행으로 항상 반환한다. "
                    + "적중률 = 100 − 오차율이며 base 로 기준 오차율(MAPE/SMAPE, 기본 SMAPE)을 고른다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/tag-predictions/accuracy/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<AccuracyStatResponse>>> getStats(
            @RequestParam("tagNo") String tagNo,
            @RequestParam("target")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime target,
            @RequestParam(value = "base", required = false) ErrorRateType base) {
        return getResponseEntity(tagPredictionAccuracyService.stats(tagNo, target, base));
    }

    @Operation(
            summary = "예측 정확도 차트 시계열",
            description = "예측시간 구간(from~to) + 유량태그(flowTagNo)·압력태그(pressureTagNo)로 유량 시계열과 압력 시계열을 "
                    + "각각 반환한다. 각 시계열은 실측 라인 + 예측구간별 예측 라인을 예측시간순으로 담으며, 실측 라인은 가장 짧은 "
                    + "예측구간의 윈도 평균을 사용한다. from/to 형식은 'yyyy-MM-dd HH:mm' 이며 미지정 시 오늘 하루."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/tag-predictions/accuracy/series", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<AccuracySeriesPairResponse>> getSeries(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime to,
            @RequestParam("flowTagNo") String flowTagNo,
            @RequestParam("pressureTagNo") String pressureTagNo) {
        return getResponseEntity(tagPredictionAccuracyService.series(from, to, flowTagNo, pressureTagNo));
    }
}
