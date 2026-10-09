package com.mindone.editor.pump.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.pump.dto.PumpCurveManualRequest;
import com.mindone.editor.pump.dto.PumpCurveRenewResponse;
import com.mindone.editor.pump.dto.PumpCurveResponse;
import com.mindone.editor.pump.dto.PumpCurveSaveRequest;
import com.mindone.editor.pump.service.PumpCurveManualService;
import com.mindone.editor.pump.service.PumpCurveRenewService;
import com.mindone.editor.pump.service.PumpCurveSaveService;
import com.mindone.editor.pump.service.PumpCurveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 펌프 성능곡선 표본 조회 명세.
 *
 * <p>{@code pump_comb_m} 의 한 펌프조합 회귀 계수(P_ADD_VAL/P_MUL_VAL/P_SQRT_MUL_VAL)를 기반으로,
 * 유량 구간 {@code [minFlow, maxFlow]} 에 대해 N 개의 (유량, 양정) 표본점을 산출해 프론트 차트 데이터로 제공한다.
 * 성능곡선의 표준 축은 X축 유량(Q, m³/h)·Y축 양정(H, m) 이며,
 * 회귀식은 {@code H(Q) = P_ADD_VAL·Q² + P_MUL_VAL·Q + P_SQRT_MUL_VAL} (레거시 {@code DrvnConfig.pressureCalValue} 의 2차식 구조 계승, Y축만 양정으로 정정).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "펌프 성능곡선", description = "펌프조합 성능곡선 표본 조회")
public class PumpCurveController extends CommonController {

    private final PumpCurveService pumpCurveService;
    private final PumpCurveRenewService pumpCurveRenewService;
    private final PumpCurveManualService pumpCurveManualService;
    private final PumpCurveSaveService pumpCurveSaveService;

    @Operation(
            summary = "펌프조합 성능곡선 표본 조회",
            description = "pump_comb_m 의 펌프조합(comb_id) 회귀 계수로 성능곡선을 그릴 (유량 Q, 양정 H) 표본점을 반환한다. "
                    + "응답은 조회한 유량 구간(minFlow=FC_MIN_VAL, maxFlow=FC_MAX_VAL)과 그 구간을 균등 분할한 표본점 목록(data)으로 구성된다. "
                    + "표본 개수는 200개로 고정한다. "
                    + "회귀식: H(Q) = quadCoef·Q² + linearCoef·Q + constCoef (X축 유량 m³/h, Y축 양정 m)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/pump-combinations/{combId}/performance-curve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<PumpCurveResponse>> getPerformanceCurve(
            @Parameter(description = "펌프조합 ID (pump_comb_m.comb_id)", example = "12")
            @PathVariable("combId") Long combId) {
        return getResponseEntity(pumpCurveService.sampleCurve(combId));
    }

    @Operation(
            summary = "펌프조합 성능곡선 갱신",
            description = "조회기간(from~to) 동안 펌프조합이 실제 운전한 실측 곡선(currCurve)과, 같은 기간 실측값으로 "
                    + "파이썬이 새로 회귀분석한 계수로 그린 새 곡선(newCurve)을 함께 반환한다. "
                    + "실측 곡선은 TB_RAWDATA 의 분별 (유량 합, 양정)이고, 새 곡선은 회귀식 H(Q)=a·Q²+b·Q+c "
                    + "(a=P_ADD_VAL, b=P_MUL_VAL, c=P_SQRT_MUL_VAL)를 실측 유량에 1:1 적용한 (유량, 회귀 양정)이다. "
                    + "함께 평균오차(avgError)·전력 원단위(powerUnit)·전력비 원단위(powerCostUnit)·우선순위(priority)를 담는다. "
                    + "from/to 형식은 'yyyy-MM-dd'(날짜 단위)이며 미지정 시 오늘 하루. 조회/계산만 하고 pump_comb_m 은 갱신하지 않는다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/pump-combinations/{combId}/performance-curve/renewal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<PumpCurveRenewResponse>> renewPerformanceCurve(
            @Parameter(description = "펌프조합 ID (pump_comb_m.comb_id)", example = "1")
            @PathVariable("combId") Long combId,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd, 미지정 시 오늘) — 그날 00:00:00 부터", example = "2026-04-09")
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd, 미지정 시 오늘) — 그날 23:59:59 까지", example = "2026-04-09")
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        return getResponseEntity(pumpCurveRenewService.renew(combId, from, to));
    }

    @Operation(
            summary = "펌프조합 성능곡선 추출 (수동 입력 점 → 회귀)",
            description = "사용자가 차트에 직접 입력한 (유량, 양정) 점(points)을 회귀 입력으로 파이썬에 전달해 새 회귀 계수를 받고, "
                    + "조회기간(from~to) 동안의 실측 유량에 그 회귀식을 적용한 새 곡선(newCurve)을 반환한다. "
                    + "실측값만으로 회귀하는 갱신(renewal)과 달리 회귀 입력이 사용자 입력 점이라는 점만 다르다. "
                    + "응답은 갱신과 동일한 구조로, 실측 곡선(currCurve)·새 회귀 곡선(newCurve)과 함께 "
                    + "평균오차(avgError)·전력 원단위(powerUnit)·전력비 원단위(powerCostUnit)·우선순위(priority)를 담는다. "
                    + "새 곡선은 회귀식 H(Q)=a·Q²+b·Q+c (a=P_ADD_VAL, b=P_MUL_VAL, c=P_SQRT_MUL_VAL)를 실측 유량에 1:1 적용한다. "
                    + "from/to 는 본문에 담되 'yyyy-MM-dd'(날짜 단위)이며 미지정 시 오늘 하루. 조회/계산만 하고 pump_comb_m 은 갱신하지 않는다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/pump-combinations/{combId}/performance-curve/extraction",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<PumpCurveRenewResponse>> extractPerformanceCurve(
            @Parameter(description = "펌프조합 ID (pump_comb_m.comb_id)", example = "1")
            @PathVariable("combId") Long combId,
            @RequestBody PumpCurveManualRequest request) {
        return getResponseEntity(pumpCurveManualService.extract(combId, request));
    }

    @Operation(
            summary = "펌프조합 성능곡선 저장",
            description = "갱신(renewal)·추출(extraction) 미리보기로 검토한 성능곡선 회귀 계수(quadCoef=P_ADD_VAL, "
                    + "linearCoef=P_MUL_VAL, constCoef=P_SQRT_MUL_VAL)와 유효 유량 구간(fcMin=FC_MIN_VAL, fcMax=FC_MAX_VAL), "
                    + "평가지표(평균오차·전력원단위·전력비원단위·우선순위·데이터수)를 pump_comb_m 의 해당 조합(combId) 행에 저장한다. "
                    + "프론트가 보낸 값을 그대로 반영하며(passthrough) 저장 시점에 파이썬을 재호출하지 않는다. "
                    + "이 프로젝트에서 pump_comb_m 에 쓰는 유일한 엔드포인트다(그 외는 모두 조회/계산만 한다). "
                    + "응답 data 는 저장된 combId 다. 유량 구간이 뒤집히면(fcMin>=fcMax) INVALID_FLOW_RANGE, "
                    + "대상 조합이 없으면 CURVE_NOT_FOUND 로 실패한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(value = "/pump-combinations/{combId}/performance-curve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<Long>> savePerformanceCurve(
            @Parameter(description = "펌프조합 ID (pump_comb_m.comb_id)", example = "1")
            @PathVariable("combId") Long combId,
            @RequestBody PumpCurveSaveRequest request) {
        return getResponseEntity(pumpCurveSaveService.save(combId, request));
    }
}
