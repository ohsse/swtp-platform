package com.mindone.editor.pump.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.pump.dto.PumpCombStatResponse;
import com.mindone.editor.pump.service.PumpCombStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 펌프조합별 운영 현황 및 전력 원단위 조회 명세.
 *
 * <p>{@code pump_comb_m}(펌프조합 마스터)에 저장된 조합별 운영대수·펌프조합·운영건수(분)·전력원단위를 그대로 표출한다.
 * 통계값은 외부 EMS/AI 프로세스가 미리 적재하므로, 이 조회는 조회 시점에 집계하지 않는 가벼운 단순 조회다.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "펌프조합 운영 현황", description = "펌프조합별 운영 현황 및 전력 원단위 조회")
public class PumpCombStatController extends CommonController {

    private final PumpCombStatService pumpCombStatService;

    @Operation(
            summary = "펌프조합별 운영 현황 / 전력 원단위 조회",
            description = "pump_comb_m 의 펌프조합(PUMP_COMB<>'')을 대상으로, 저장된 전체 컬럼을 그대로 반환한다. "
                    + "운영대수·펌프조합·우선순위·운영건수(분)뿐 아니라 성능곡선 회귀 계수(quad/linear/const)·유효 유량 구간(fcMin/fcMax)·"
                    + "평균오차·전력 원단위·전력비 원단위까지 모두 담아, 프론트가 운영 현황 외 다른 화면도 함께 그릴 수 있게 한다. "
                    + "운영건수(분)·전력원단위 등 통계값은 외부 EMS/AI 프로세스가 pump_comb_m 에 미리 적재한 값이며, "
                    + "조회 시점에 집계하지 않으므로 가볍다(미적재 값은 null)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/pump-combinations/operation-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<PumpCombStatResponse>>> getOperationStats() {
        return getResponseEntity(pumpCombStatService.stats());
    }
}
