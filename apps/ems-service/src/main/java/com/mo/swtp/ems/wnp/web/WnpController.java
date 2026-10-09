package com.mo.swtp.ems.wnp.web;

import java.util.List;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.ems.wnp.dto.WnpAddListRequest;
import com.mo.swtp.ems.wnp.dto.WnpAddRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyListRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyRequest;
import com.mo.swtp.ems.wnp.dto.WnpResponse;
import com.mo.swtp.ems.wnp.service.WnpService;

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
 * 수계통지점 API.
 *
 * <p>제어그룹과 별개의 표면을 갖는다 — 제어그룹을 편성하기 전에 지점 목록이 먼저 있어야 하고,
 * 배관 구조라 변경 주기도 다르다. 그것이 두 도메인으로 가른 근거다(문서 02 「결정 2」).
 * <b>제어그룹과 태그를 공유하지 않는 이유도 같다</b> — 프론트가 지점 목록을 찾을 자리는
 * 제어그룹 편성 화면이 아니라 자기 화면이다.
 *
 * <p>게이트웨이 경유 외부 주소는 {@code /ems-service/api/ems/wnp}다.
 * 물리 삭제 엔드포인트를 두지 않는다 — 해제는 {@code useYn=N} 수정으로 표현한다.
 *
 * <p><b>swagger의 {@code @ApiResponse}는 FQN으로 쓴다</b> — 공용 응답 봉투 {@link ApiResponse}와
 * 단순명이 충돌하므로 빈도가 낮은 쪽을 FQN으로 미룬다(apps/CLAUDE.md 「API 문서화」).
 */
@Tag(name = "수계통지점",
        description = "EMS 수계통지점 마스터. 삭제 API가 없다 — 해제는 useYn=N 수정으로 표현한다")
@RestController
@RequestMapping("/api/ems/wnp")
@RequiredArgsConstructor
public class WnpController {

    private final WnpService wnpService;

    @Operation(summary = "수계통지점 목록 조회",
            description = """
                    정렬순서 오름차순으로 반환한다. 정렬순서가 없는 건은 뒤로 가고, 같은 순서끼리는 수계통지점ID로 확정된다 \
                    — 같은 요청은 항상 같은 순서를 돌려준다.

                    결과가 없으면 빈 배열과 함께 200이다(404가 아니다). 신규 정수장은 수계통지점 0건에서 출발하므로 \
                    "아직 없음"과 "조회 실패"를 구분할 수 있어야 한다.""")
    @GetMapping
    public ApiResponse<List<WnpResponse>> getWnpList(
            @Parameter(description = "사용여부 필터. 생략하면 사용·미사용을 모두 반환한다")
            @RequestParam(required = false) UseYn useYn) {
        return ApiResponse.ok(wnpService.getWnpList(useYn));
    }

    @Operation(summary = "수계통지점 단건 조회",
            description = "목록과 달리 대상이 없으면 404다 — 지목한 자원이 없는 것은 실제 오류다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 수계통지점ID가 없다")
    @GetMapping("/{wnpId}")
    public ApiResponse<WnpResponse> getWnp(
            @Parameter(description = "수계통지점ID") @PathVariable String wnpId) {
        return ApiResponse.ok(wnpService.getWnp(wnpId));
    }

    @Operation(summary = "수계통지점 등록",
            description = """
                    이미 있는 수계통지점ID로 호출하면 409다 — 조용한 수정이 되지 않는다. \
                    useYn을 생략하면 Y로 등록된다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 이미 존재하는 수계통지점ID")
    @PostMapping
    public ApiResponse<WnpResponse> addWnp(@RequestBody @Valid WnpAddRequest request) {
        return ApiResponse.ok(wnpService.addWnp(request));
    }

    @Operation(summary = "수계통지점 일괄 등록",
            description = """
                    전부 성공하거나 전부 실패한다 — 일부만 등록된 상태가 남지 않는다.

                    409가 되는 경우가 둘이다: 요청 안에 같은 수계통지점ID가 두 번 들어 있거나, \
                    그중 하나라도 이미 등록돼 있는 경우다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안의 수계통지점ID 중복 또는 이미 존재하는 수계통지점ID")
    @PostMapping("/list")
    public ApiResponse<List<WnpResponse>> addWnpList(@RequestBody @Valid WnpAddListRequest request) {
        return ApiResponse.ok(wnpService.addWnpList(request));
    }

    @Operation(summary = "수계통지점 단건 수정",
            description = """
                    수정 대상은 경로가 정한다 — 본문에 식별자를 두지 않아 둘이 어긋날 여지를 없앤다.

                    부분 수정이다. 본문에서 생략하거나 null로 보낸 필드는 바뀌지 않는다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 수계통지점ID가 없다")
    @PutMapping("/{wnpId}")
    public ApiResponse<WnpResponse> modifyWnp(
            @Parameter(description = "수정할 수계통지점ID") @PathVariable String wnpId,
            @RequestBody @Valid WnpModifyRequest request) {
        return ApiResponse.ok(wnpService.modifyWnp(wnpId, request));
    }

    @Operation(summary = "수계통지점 일괄 수정",
            description = """
                    단건과 달리 각 항목이 자기 수계통지점ID를 담는다 — 일괄 경로에는 대상을 가리킬 경로 변수가 없다.

                    하나라도 없으면 아무것도 바꾸지 않고 404다. 일부만 반영된 상태가 남으면 \
                    재요청이 무엇을 다시 보내야 하는지 알 수 없기 때문이다.

                    응답은 요청과 같은 순서로 돌아온다 — 위치로 짝지어도 안전하다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 요청한 수계통지점ID 중 없는 것이 있다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안에 같은 수계통지점ID가 두 번 들어 있다")
    @PutMapping("/list")
    public ApiResponse<List<WnpResponse>> modifyWnpList(
            @RequestBody @Valid WnpModifyListRequest request) {
        return ApiResponse.ok(wnpService.modifyWnpList(request));
    }
}
