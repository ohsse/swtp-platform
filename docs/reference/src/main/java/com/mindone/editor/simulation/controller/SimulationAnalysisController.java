package com.mindone.editor.simulation.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.simulation.dto.SimulationAnalysisResponse;
import com.mindone.editor.simulation.service.SimulationAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관망해석 시뮬레이션 분석시각 조회 명세.
 *
 * <p>분석일시(분 단위)를 기준으로 정수장 송수유량 / 펌프조합 / 노드별 수요량을 공유 EMS DB 에서 함께 조회한다(조회 전용).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "관망해석 시뮬레이션", description = "분석시각 기준 송수유량/펌프조합/수요량 조회")
public class SimulationAnalysisController extends CommonController {

    private final SimulationAnalysisService simulationAnalysisService;

    @Operation(
            summary = "분석시각 조회",
            description = "분석일시(yyyy-MM-dd HH:mm) 기준으로 정수장 송수유량(outFlow), 펌프조합(pumpComb), "
                    + "노드별 수요량(demands)을 함께 조회한다. 해당 분 데이터가 없는 항목은 null 이다. "
                    + "경로변수의 공백은 URL 인코딩(%20)해야 한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/simulations/{analDateTime}/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<SimulationAnalysisResponse>> analyze(
            @Parameter(description = "분석일시(yyyy-MM-dd HH:mm)", example = "2026-07-08 10:30")
            @PathVariable("analDateTime") String analDateTime) {
        return getResponseEntity(simulationAnalysisService.analyze(analDateTime));
    }
}
