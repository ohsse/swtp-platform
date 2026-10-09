package com.mo.swtp.master.prcs.web;


import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.prcs.service.PrcsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.mo.swtp.master.prcs.dto.PrcsAddListRequest;
import com.mo.swtp.master.prcs.dto.PrcsAddRequest;
import com.mo.swtp.master.prcs.dto.PrcsModifyListRequest;
import com.mo.swtp.master.prcs.dto.PrcsModifyRequest;
import com.mo.swtp.master.prcs.dto.PrcsResponse;
import java.util.List;

/**
 * 공정 API.
 *
 * <p>공정은 운전모드 변경이력의 제어대상이기도 하다 — {@code ControlTargetType.PRCS}일 때
 * {@code ctrlTrgtId}가 여기의 {@code prcsId}다. 그래서 <b>물리 삭제 엔드포인트를 두지 않는다</b>:
 * 지우면 그 공정이 과거에 무슨 모드였는지를 조회할 수 없게 된다.
 *
 * <p><b>swagger의 {@code @ApiResponse}는 FQN으로 쓴다</b> — 공용 응답 봉투 {@link ApiResponse}와
 * 단순명이 충돌하므로 빈도가 낮은 쪽을 FQN으로 미룬다(apps/CLAUDE.md 「API 문서화」).
 */
@Tag(name = "공정",
        description = "공정 기준정보. 삭제 API가 없다 — 해제는 useYn=N 수정으로 표현한다. 운전모드 변경이력이 참조하는 축이다")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/master/prcs")
public class PrcsController {

    private final PrcsService prcsService;

    @Operation(summary = "공정 목록 조회",
            description = """
                    정렬순서 오름차순으로 반환한다. 정렬순서가 없는 건은 뒤로 간다.

                    <b>같은 정렬순서끼리의 순서는 보장되지 않는다</b> — 타이브레이커가 없어 같은 요청이 \
                    다른 순서를 돌려줄 수 있다(05 「알려진 한계」 3).

                    결과가 없으면 빈 배열과 함께 200이다(404가 아니다).""")
    @GetMapping
    public ApiResponse<List<PrcsResponse>> getPrcsList(
            @Parameter(description = "사용여부 필터. 생략하면 사용·미사용을 모두 반환한다")
            @RequestParam(required = false) UseYn useYn) {
        return ApiResponse.ok(prcsService.getPrcsList(useYn));
    }

    @Operation(summary = "공정 단건 조회",
            description = "목록과 달리 대상이 없으면 404다 — 지목한 자원이 없는 것은 실제 오류다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 공정ID가 없다")
    @GetMapping("/{prcsId}")
    public ApiResponse<PrcsResponse> getPrcsByPrcsId(
            @Parameter(description = "공정ID") @PathVariable String prcsId) {
        return ApiResponse.ok(prcsService.getPrcsById(prcsId));
    }

    @Operation(summary = "공정타입별 목록 조회",
            description = "목록이므로 해당 타입의 공정이 없어도 빈 배열과 함께 200이다(404가 아니다)")
    @GetMapping("/type/{prcsTypeCd}")
    public ApiResponse<List<PrcsResponse>> getPrcsByPrcsTypeCd(
            @Parameter(description = "공정타입코드") @PathVariable String prcsTypeCd) {
        return ApiResponse.ok(prcsService.getPrcsByPrcsTypeCd(prcsTypeCd));
    }

    @Operation(summary = "공정 등록",
            description = """
                    useYn을 생략하면 Y로 등록된다.

                    <b>이미 있는 공정ID로 호출하면 409가 아니라 기존 행이 조용히 수정된다.</b> \
                    등록과 수정을 구분해서 부르는 쪽이 안전하다 — 중복 방어는 아직 없다(05 「알려진 한계」 2).""")
    @PostMapping
    public ApiResponse<PrcsResponse> addPrcs(@RequestBody @Valid PrcsAddRequest request) {
        return ApiResponse.ok(prcsService.addPrcs(request));
    }

    @Operation(summary = "공정 일괄 등록",
            description = """
                    전부 성공하거나 전부 실패한다 — 일부만 등록된 상태가 남지 않는다.

                    단건과 마찬가지로 이미 있는 공정ID는 조용히 수정된다. \
                    요청 안에 같은 공정ID를 두 번 담으면 뒤의 것만 남는다(05 「알려진 한계」 2).""")
    @PostMapping("/list")
    public ApiResponse<List<PrcsResponse>> addPrcsList(@RequestBody @Valid PrcsAddListRequest requests) {
        return ApiResponse.ok(prcsService.addPrcsList(requests));
    }

    @Operation(summary = "공정 단건 수정",
            description = """
                    부분 수정이다. 본문에서 생략하거나 null로 보낸 필드는 바뀌지 않는다.

                    <b>수정 대상은 본문의 prcsId가 정한다 — 경로의 prcsId는 현재 무시된다.</b> \
                    둘을 다르게 보내면 본문 쪽이 수정되므로 같은 값을 보내야 한다(05 「알려진 한계」 1).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 본문의 공정ID가 없다")
    @PutMapping("/{prcsId}")
    public ApiResponse<PrcsResponse> modifyPrcs(@RequestBody @Valid PrcsModifyRequest request) {
        return ApiResponse.ok(prcsService.modifyPrcs(request));
    }

    @Operation(summary = "공정 일괄 수정",
            description = """
                    각 항목이 자기 공정ID를 담는다. 하나라도 없으면 아무것도 바꾸지 않고 404다.

                    <b>응답 순서는 요청 순서와 다를 수 있다</b> — 조회 순서가 DB가 정하는 대로다. \
                    위치로 짝짓지 말고 공정ID로 맞춰야 한다.

                    요청 안에 같은 공정ID를 두 번 담으면 안 된다 — 중복 방어가 없어 500이 난다(05 「알려진 한계」 2).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 요청한 공정ID 중 없는 것이 있다")
    @PutMapping("/list")
    public ApiResponse<List<PrcsResponse>> modifyPrcsList(@RequestBody @Valid PrcsModifyListRequest request) {
        return ApiResponse.ok(prcsService.modifyPrcsList(request));
    }
}
