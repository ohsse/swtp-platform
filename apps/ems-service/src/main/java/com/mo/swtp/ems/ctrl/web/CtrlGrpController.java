package com.mo.swtp.ems.ctrl.web;

import java.util.List;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddListRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyListRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpResponse;
import com.mo.swtp.ems.ctrl.service.CtrlGrpService;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제어그룹 API.
 *
 * <p>경로에 {@code /ems-service} 접두사를 붙이지 않는다 — 게이트웨이의 {@code RewritePath}가 벗긴 뒤 도달한다.
 * 게이트웨이 경유 외부 주소는 {@code /ems-service/api/ems/ctrl-grp}다.
 *
 * <p><b>복합어 도메인의 경로 규약을 여기서 정한다</b> — 언더스코어를 하이픈으로 바꾸고 테이블명과 1:1로 맞춘다
 * ({@code ctrl_grp_m} → {@code ctrl-grp}). master는 전부 단일 세그먼트라 선례가 없었다.
 *
 * <p>물리 삭제 엔드포인트를 두지 않는다 — 해제는 {@code useYn=N} 수정으로 표현한다.
 * 제어 이력이 참조하는 축이라 지우면 "작년에 이 펌프를 어느 그룹으로 돌렸나"를 조회할 수 없게 된다.
 *
 * <p><b>swagger의 {@code @ApiResponse}는 FQN으로 쓴다</b> — 공용 응답 봉투
 * {@link ApiResponse}와 단순명이 충돌하는데, 봉투는 모든 핸들러의 반환 타입에 나오고
 * 애노테이션은 도메인 고유 에러에만 붙으므로 빈도가 낮은 쪽을 FQN으로 미룬다(apps/CLAUDE.md 「API 문서화」).
 */
@Tag(name = "제어그룹",
        description = "EMS 제어그룹 마스터. 삭제 API가 없다 — 해제는 useYn=N 수정으로 표현한다")
@RestController
@RequestMapping("/api/ems/ctrl-grp")
@RequiredArgsConstructor
public class CtrlGrpController {

    private final CtrlGrpService ctrlGrpService;

    @Operation(summary = "제어그룹 목록 조회",
            description = """
                    정렬순서 오름차순으로 반환한다. 정렬순서가 없는 건은 뒤로 가고, 같은 순서끼리는 제어그룹ID로 확정된다 \
                    — 같은 요청은 항상 같은 순서를 돌려준다.

                    결과가 없으면 빈 배열과 함께 200이다(404가 아니다). 신규 정수장은 제어그룹 0건에서 출발하므로 \
                    "아직 없음"과 "조회 실패"를 구분할 수 있어야 한다.""")
    @GetMapping
    public ApiResponse<List<CtrlGrpResponse>> getCtrlGrpList(
            @Parameter(description = "사용여부 필터. 생략하면 사용·미사용을 모두 반환한다")
            @RequestParam(required = false) UseYn useYn) {
        return ApiResponse.ok(ctrlGrpService.getCtrlGrpList(useYn));
    }

    @Operation(summary = "제어그룹 단건 조회",
            description = "목록과 달리 대상이 없으면 404다 — 지목한 자원이 없는 것은 실제 오류다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @GetMapping("/{ctrlGrpId}")
    public ApiResponse<CtrlGrpResponse> getCtrlGrp(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId) {
        return ApiResponse.ok(ctrlGrpService.getCtrlGrp(ctrlGrpId));
    }

    @Operation(summary = "제어그룹 등록",
            description = """
                    이미 있는 제어그룹ID로 호출하면 409다 — 조용한 수정이 되지 않는다. \
                    useYn을 생략하면 Y로 등록된다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 이미 존재하는 제어그룹ID")
    @PostMapping
    public ApiResponse<CtrlGrpResponse> addCtrlGrp(@RequestBody @Valid CtrlGrpAddRequest request) {
        return ApiResponse.ok(ctrlGrpService.addCtrlGrp(request));
    }

    @Operation(summary = "제어그룹 일괄 등록",
            description = """
                    전부 성공하거나 전부 실패한다 — 일부만 등록된 상태가 남지 않는다.

                    409가 되는 경우가 둘이다: 요청 안에 같은 제어그룹ID가 두 번 들어 있거나, \
                    그중 하나라도 이미 등록돼 있는 경우다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안의 제어그룹ID 중복 또는 이미 존재하는 제어그룹ID")
    @PostMapping("/list")
    public ApiResponse<List<CtrlGrpResponse>> addCtrlGrpList(@RequestBody @Valid CtrlGrpAddListRequest request) {
        return ApiResponse.ok(ctrlGrpService.addCtrlGrpList(request));
    }

    @Operation(summary = "제어그룹 단건 수정",
            description = """
                    수정 대상은 경로가 정한다 — 본문에 식별자를 두지 않아 둘이 어긋날 여지를 없앤다.

                    부분 수정이다. 본문에서 생략하거나 null로 보낸 필드는 바뀌지 않는다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @PutMapping("/{ctrlGrpId}")
    public ApiResponse<CtrlGrpResponse> modifyCtrlGrp(
            @Parameter(description = "수정할 제어그룹ID") @PathVariable String ctrlGrpId,
            @RequestBody @Valid CtrlGrpModifyRequest request) {
        return ApiResponse.ok(ctrlGrpService.modifyCtrlGrp(ctrlGrpId, request));
    }

    @Operation(summary = "제어그룹 일괄 수정",
            description = """
                    단건과 달리 각 항목이 자기 제어그룹ID를 담는다 — 일괄 경로에는 대상을 가리킬 경로 변수가 없다.

                    하나라도 없으면 아무것도 바꾸지 않고 404다. 일부만 반영된 상태가 남으면 \
                    재요청이 무엇을 다시 보내야 하는지 알 수 없기 때문이다.

                    응답은 요청과 같은 순서로 돌아온다 — 위치로 짝지어도 안전하다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 요청한 제어그룹ID 중 없는 것이 있다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안에 같은 제어그룹ID가 두 번 들어 있다")
    @PutMapping("/list")
    public ApiResponse<List<CtrlGrpResponse>> modifyCtrlGrpList(
            @RequestBody @Valid CtrlGrpModifyListRequest request) {
        return ApiResponse.ok(ctrlGrpService.modifyCtrlGrpList(request));
    }
}
