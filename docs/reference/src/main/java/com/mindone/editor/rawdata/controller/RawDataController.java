package com.mindone.editor.rawdata.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.rawdata.dto.RawDataLatestResponse;
import com.mindone.editor.rawdata.service.RawDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실측 태그값 조회 명세.
 *
 * <p>외부 SCADA 수집 모듈이 공유 EMS DB({@code TB_RAWDATA})에 적재한 실측값을 조회한다(조회 전용).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "실측 태그값", description = "SCADA 실측 태그값(TB_RAWDATA) 조회")
public class RawDataController extends CommonController {

    private final RawDataService rawDataService;

    @Operation(
            summary = "오늘 마지막 실측값 조회",
            description = "특정 태그의 오늘(자정~현재) 계측분 중 가장 최근 1건을 조회한다. "
                    + "오늘 계측된 값이 없으면 data 는 null 이다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/tag-measurements/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<RawDataLatestResponse>> getLatestToday(
            @Parameter(description = "태그번호(SCADA 태그)")
            @RequestParam("tagNo") String tagNo) {
        return getResponseEntity(rawDataService.latestToday(tagNo));
    }
}
