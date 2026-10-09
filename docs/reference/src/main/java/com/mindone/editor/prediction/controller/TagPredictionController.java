package com.mindone.editor.prediction.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.dto.TagPredValueResponse;
import com.mindone.editor.prediction.service.TagPredictionService;
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
 * 태그 예측값 조회 명세.
 *
 * <p>외부 예측 모듈이 공유 DB 에 기록한 SCADA 태그 예측값을 조회한다(조회 전용).
 * 태그번호 기준으로 조회하며, 예측시간 구간·예측구간으로 좁힐 수 있다.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "태그 예측값", description = "SCADA 태그 예측값 조회")
public class TagPredictionController extends CommonController {

    private final TagPredictionService tagPredictionService;

    @Operation(
            summary = "태그 예측값 조회",
            description = "태그번호 기준으로 예측값을 조회한다. 예측시간 구간(predFrom/predTo)과 예측구간(duration)은 선택 필터다. "
                    + "예측시간 오름차순, 같은 예측시간 안에서는 예측구간(짧은 것 → 긴 것) 순으로 정렬한다. "
                    + "특정 예측시간 1건의 5종 예측구간을 묶어 받으려면 predFrom/predTo 에 같은 시각을 지정한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/tag-predictions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<TagPredValueResponse>>> getPredictions(
            @RequestParam("tagNo") String tagNo,
            @RequestParam(value = "predFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime predFrom,
            @RequestParam(value = "predTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime predTo,
            @RequestParam(value = "duration", required = false) PredictionDuration duration) {
        return getResponseEntity(tagPredictionService.search(tagNo, predFrom, predTo, duration));
    }
}
