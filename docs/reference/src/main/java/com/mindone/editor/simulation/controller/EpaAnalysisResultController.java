package com.mindone.editor.simulation.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.simulation.dto.EpaAnalysisResultItem;
import com.mindone.editor.simulation.service.EpaAnalysisResultService;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 관망해석 해석결과 조회 명세.
 *
 * <p>표시대상({@code TB_EPA_TAG_INFO.IS_DISPLAY = 1}) 위치별로, 분석일시(분 단위) 기준 유량/압력 계측값과
 * 그에 5% 미만 오차를 부여한 해석값(목업)을 {@code DISPLAY_ORDER} 순으로 조회한다(조회 전용).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "관망해석 해석결과", description = "분석시각 기준 위치별 유량/압력 계측값 + 해석값 조회")
public class EpaAnalysisResultController extends CommonController {

    private final EpaAnalysisResultService epaAnalysisResultService;

    @Operation(
            summary = "해석결과 조회",
            description = "분석일시(yyyy-MM-dd HH:mm) 기준으로 표시대상 위치별 유량(flow)/압력(press) 계측값과 "
                    + "해석값(계측값에 ±5% 미만 랜덤 오차를 적용한 목업)을 DISPLAY_ORDER 순으로 조회한다. "
                    + "태그가 없거나 해당 분 계측이 없는 값은 null 이다. 경로변수의 공백은 URL 인코딩(%20)해야 한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/simulations/{analDateTime}/analysis-result", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<EpaAnalysisResultItem>>> analyze(
            @Parameter(description = "분석일시(yyyy-MM-dd HH:mm)", example = "2026-04-10 10:30")
            @PathVariable("analDateTime") String analDateTime) {
        // 실제 관망해석 소요 시간을 모사하기 위해 3~5분(180~300초) 랜덤 지연 후 응답한다.
        // 단, 이미 캐시된 분석일시는 지연 없이 즉시 응답한다.
        if (!epaAnalysisResultService.isCached(analDateTime)) {
            delayForAnalysis();
        }
        return getResponseEntity(epaAnalysisResultService.analyze(analDateTime));
    }

    /**
     * 응답 전 3~5분 사이의 랜덤 지연을 준다.
     *
     * <p>인터럽트가 발생하면 현재 스레드의 인터럽트 상태를 복원하고 즉시 반환한다.</p>
     */
    private void delayForAnalysis() {
        long delayMillis = ThreadLocalRandom.current().nextLong(180_000L, 300_001L);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
