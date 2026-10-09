package com.mindone.editor.inp.opt.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.inp.opt.domain.OptimizeStatus;
import com.mindone.editor.inp.opt.dto.InpOptHistDetailResponse;
import com.mindone.editor.inp.opt.dto.InpOptHistResponse;
import com.mindone.editor.inp.opt.service.InpOptHistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "INP 최적화", description = "INP 파일 최적화 이력 관리")
public class InpFileOptHistController extends CommonController {
    private final InpOptHistService service;

    @Operation(
            summary = "최적화 실행",
            description = "특정 INP 파일에 최적화를 실행한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/opt/{inpFileId}")
    public ResponseEntity<ResponseObject<Long>> runOptimizer (@PathVariable("inpFileId") String inpFileId) {
        // 이력 저장 후 발급된 이력 ID 를 반환한다(파이썬 최적화 요청은 비동기로 별도 진행).
        return getResponseEntity(service.run(inpFileId));
    }

    @Operation(
            summary = "최적화 이력 목록 조회",
            description = "INP 파일 ID 와 진행상태코드로 최적화 이력을 조회한다. "
                    + "두 파라미터 모두 선택값이며, 응답에는 진행세대수/총세대수로 계산한 진행률(%)이 포함된다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/opt")
    public ResponseEntity<ResponseObject<List<InpOptHistResponse>>> getOptHistList (
            @Parameter(description = "INP 파일 ID(없으면 전체)")
            @RequestParam(value = "inpFileId", required = false) String inpFileId,
            @Parameter(description = "진행상태코드(READY/RUNNING/COMPLETED/ERROR, 없으면 전체)")
            @RequestParam(value = "statusCd", required = false) OptimizeStatus statusCd
    ) {
        return getResponseEntity(service.getList(inpFileId, statusCd));
    }

    @Operation(
            summary = "최적화 이력 상세 조회",
            description = "이력 ID 로 최적화 이력 상세를 조회한다. 최적화 전/후 결과를 "
                    + "지점명 + 데이터유형 기준으로 병합한 비교 행(계측값, 전/후 분석값, 전/후 오차율, 개선율)을 제공한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/opt/hist/{histId}")
    public ResponseEntity<ResponseObject<InpOptHistDetailResponse>> getOptHistDetail (
            @PathVariable("histId") Long histId
    ) {
        return getResponseEntity(service.getDetail(histId));
    }
}
