package com.mo.swtp.ems.ctrl.web;

import java.util.List;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpEqpReplaceRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpMemberReplaceResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpMemberResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpTagReplaceRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpWnpReplaceRequest;
import com.mo.swtp.ems.ctrl.service.CtrlGrpMemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제어그룹 편성 API — 설비·수계통지점·태그.
 *
 * <p><b>세 자원 모두 {@code PUT} 하나뿐이고 {@code POST}·{@code DELETE}가 없다.</b>
 * 편성은 컬렉션 전체 교체이기 때문이다 — 개별 추가·삭제를 열면
 * "1계열 펌프를 2계열로 옮기기"가 2요청으로 갈리고 그 사이에 어느 그룹에도 속하지 않은 설비가
 * DB에 남는다(문서 03 「결정 1」). 편성 해제는 빈 배열 저장이다.
 *
 * <p>편성 명세 3종은 {@code ctrl} 도메인이 소유한다 — 소유 규칙 ①("그 관계를 만들고 끊는 API를
 * 소유하는 도메인")로 갈린다. {@code ctrl_grp_wnp_p}는 PK가 {@code wnp_id} 단독이라 규칙 ②로 재면
 * {@code wnp}가 되지만, <b>①에서 이미 갈리므로 ②까지 가지 않는다.</b>
 *
 * <p>게이트웨이 경유 외부 주소는 {@code /ems-service/api/ems/ctrl-grp/{ctrlGrpId}/eqp} 꼴이다.
 *
 * <p><b>{@link CtrlGrpController}와 같은 태그를 공유한다</b> — 편성은 제어그룹 상세 화면의 일부이고,
 * 프론트가 "제어그룹 편성"을 찾을 자리는 "제어그룹" 묶음 안이다(apps/CLAUDE.md 「API 문서화」).
 */
@Tag(name = "제어그룹")
@RestController
@RequestMapping("/api/ems/ctrl-grp/{ctrlGrpId}")
@RequiredArgsConstructor
public class CtrlGrpMemberController {

    private final CtrlGrpMemberService ctrlGrpMemberService;

    @Operation(summary = "설비 편성 조회",
            description = "정렬순서 오름차순. 편성이 없으면 빈 배열과 함께 200이다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @GetMapping("/eqp")
    public ApiResponse<List<CtrlGrpMemberResponse>> getEqpMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId) {
        return ApiResponse.ok(ctrlGrpMemberService.getEqpMembers(ctrlGrpId));
    }

    @Operation(summary = "설비 편성 전체 교체",
            description = """
                    보낸 배열이 편성의 전부가 된다 — 기존 편성 중 배열에 없는 설비는 빠진다. \
                    <b>배열 순서가 곧 정렬순서(1부터)</b>이므로 재정렬 전용 API가 따로 없다. \
                    빈 배열은 편성 해제다.

                    다른 그룹에 편성돼 있던 설비를 보내면 그쪽에서 빼 온다(거절하지 않는다). \
                    그렇게 빠진 설비는 응답의 <code>stolen</code>에 출처 그룹과 함께 실린다 — \
                    비어 있으면 이번 저장이 다른 그룹을 건드리지 않았다는 뜻이다.

                    사용여부가 N인 그룹에도 저장할 수 있다. 비활성 그룹이 설비를 붙잡고 놓지 않으면 \
                    운영자가 빠져나갈 길이 없어지기 때문이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안에 같은 설비ID가 두 번 들어 있다")
    @PutMapping("/eqp")
    public ApiResponse<CtrlGrpMemberReplaceResponse> replaceEqpMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId,
            @RequestBody @Valid CtrlGrpEqpReplaceRequest request) {
        return ApiResponse.ok(ctrlGrpMemberService.replaceEqpMembers(ctrlGrpId, request.eqpIds()));
    }

    @Operation(summary = "수계통지점 편성 조회",
            description = "정렬순서 오름차순. 편성이 없으면 빈 배열과 함께 200이다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @GetMapping("/wnp")
    public ApiResponse<List<CtrlGrpMemberResponse>> getWnpMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId) {
        return ApiResponse.ok(ctrlGrpMemberService.getWnpMembers(ctrlGrpId));
    }

    @Operation(summary = "수계통지점 편성 전체 교체",
            description = "설비 편성과 규칙이 같다 — 배열 순서가 정렬순서, 빈 배열은 해제, 뺏어온 건은 stolen에 실린다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안에 같은 수계통지점ID가 두 번 들어 있다")
    @PutMapping("/wnp")
    public ApiResponse<CtrlGrpMemberReplaceResponse> replaceWnpMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId,
            @RequestBody @Valid CtrlGrpWnpReplaceRequest request) {
        return ApiResponse.ok(ctrlGrpMemberService.replaceWnpMembers(ctrlGrpId, request.wnpIds()));
    }

    @Operation(summary = "태그 편성 조회",
            description = "정렬순서 오름차순. 편성이 없으면 빈 배열과 함께 200이다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @GetMapping("/tag")
    public ApiResponse<List<CtrlGrpMemberResponse>> getTagMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId) {
        return ApiResponse.ok(ctrlGrpMemberService.getTagMembers(ctrlGrpId));
    }

    @Operation(summary = "태그 편성 전체 교체",
            description = "설비 편성과 규칙이 같다 — 배열 순서가 정렬순서, 빈 배열은 해제, 뺏어온 건은 stolen에 실린다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 제어그룹ID가 없다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "EMS-409 — 요청 안에 같은 태그시리얼번호가 두 번 들어 있다")
    @PutMapping("/tag")
    public ApiResponse<CtrlGrpMemberReplaceResponse> replaceTagMembers(
            @Parameter(description = "제어그룹ID") @PathVariable String ctrlGrpId,
            @RequestBody @Valid CtrlGrpTagReplaceRequest request) {
        return ApiResponse.ok(ctrlGrpMemberService.replaceTagMembers(ctrlGrpId, request.tagSns()));
    }
}
