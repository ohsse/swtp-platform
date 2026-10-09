package com.mo.swtp.master.fclt.web;


import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.master.fclt.service.FcltService;
import com.mo.swtp.master.support.UseYn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.mo.swtp.master.fclt.dto.FcltAddListRequest;
import com.mo.swtp.master.fclt.dto.FcltAddRequest;
import com.mo.swtp.master.fclt.dto.FcltModifyListRequest;
import com.mo.swtp.master.fclt.dto.FcltModifyRequest;
import com.mo.swtp.master.fclt.dto.FcltResponse;
import java.util.List;

/**
 * 시설 API.
 *
 * <p>설비가 소속되는 상위 축이다. <b>다만 설비 쪽에서 이 시설의 존재를 검사하지 않는다</b> —
 * DDL에 FK가 없고 엔티티에 연관관계도 두지 않으므로, 시설을 지우는 대신 {@code useYn=N}으로 내려도
 * 그 시설을 가리키는 설비는 그대로 남는다.
 *
 * <p><b>swagger의 {@code @ApiResponse}는 FQN으로 쓴다</b> — 공용 응답 봉투 {@link ApiResponse}와
 * 단순명이 충돌하므로 빈도가 낮은 쪽을 FQN으로 미룬다(apps/CLAUDE.md 「API 문서화」).
 */
@Tag(name = "시설",
        description = "시설 기준정보. 삭제 API가 없다 — 해제는 useYn=N 수정으로 표현한다. 설비가 소속되는 상위 축이다")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/master/fclt")
public class FcltController {

    private final FcltService fcltService;

    @Operation(summary = "시설 목록 조회",
            description = """
                    정렬순서 오름차순으로 반환한다. 정렬순서가 없는 건은 뒤로 간다.

                    <b>같은 정렬순서끼리의 순서는 보장되지 않는다</b> — 타이브레이커가 없어 같은 요청이 \
                    다른 순서를 돌려줄 수 있다(05 「알려진 한계」 3).

                    결과가 없으면 빈 배열과 함께 200이다(404가 아니다).""")
    @GetMapping
    public ApiResponse<List<FcltResponse>> getFcltList(
            @Parameter(description = "사용여부 필터. 생략하면 사용·미사용을 모두 반환한다")
            @RequestParam(required = false) UseYn useYn) {
        return ApiResponse.ok(fcltService.getFcltList(useYn));
    }

    @Operation(summary = "시설 단건 조회",
            description = "목록과 달리 대상이 없으면 404다 — 지목한 자원이 없는 것은 실제 오류다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 시설ID가 없다")
    @GetMapping("/{fcltId}")
    public ApiResponse<FcltResponse> getFcltByFcltId(
            @Parameter(description = "시설ID") @PathVariable String fcltId) {
        return ApiResponse.ok(fcltService.getFcltById(fcltId));
    }

    @Operation(summary = "시설타입별 목록 조회",
            description = "목록이므로 해당 타입의 시설이 없어도 빈 배열과 함께 200이다(404가 아니다)")
    @GetMapping("/type/{fcltTypeCd}")
    public ApiResponse<List<FcltResponse>> getFcltByFcltTypeCd(
            @Parameter(description = "시설타입코드") @PathVariable String fcltTypeCd) {
        return ApiResponse.ok(fcltService.getFcltByFcltTypeCd(fcltTypeCd));
    }

    @Operation(summary = "시설 등록",
            description = """
                    useYn을 생략하면 Y로 등록된다.

                    <b>이미 있는 시설ID로 호출하면 409가 아니라 기존 행이 조용히 수정된다.</b> \
                    등록과 수정을 구분해서 부르는 쪽이 안전하다 — 중복 방어는 아직 없다(05 「알려진 한계」 2).""")
    @PostMapping
    public ApiResponse<FcltResponse> addFclt(@RequestBody @Valid FcltAddRequest request) {
        return ApiResponse.ok(fcltService.addFclt(request));
    }

    @Operation(summary = "시설 일괄 등록",
            description = """
                    전부 성공하거나 전부 실패한다 — 일부만 등록된 상태가 남지 않는다.

                    단건과 마찬가지로 이미 있는 시설ID는 조용히 수정된다. \
                    요청 안에 같은 시설ID를 두 번 담으면 뒤의 것만 남는다(05 「알려진 한계」 2).""")
    @PostMapping("/list")
    public ApiResponse<List<FcltResponse>> addFcltList(@RequestBody @Valid FcltAddListRequest requests) {
        return ApiResponse.ok(fcltService.addFclts(requests));
    }

    @Operation(summary = "시설 단건 수정",
            description = """
                    부분 수정이다. 본문에서 생략하거나 null로 보낸 필드는 바뀌지 않는다.

                    <b>수정 대상은 본문의 fcltId가 정한다 — 경로의 fcltId는 현재 무시된다.</b> \
                    둘을 다르게 보내면 본문 쪽이 수정되므로 같은 값을 보내야 한다(05 「알려진 한계」 1).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 본문의 시설ID가 없다")
    @PutMapping("/{fcltId}")
    public ApiResponse<FcltResponse> modifyFclt(@RequestBody @Valid FcltModifyRequest request) {
        return ApiResponse.ok(fcltService.modifyFclt(request));
    }

    @Operation(summary = "시설 일괄 수정",
            description = """
                    각 항목이 자기 시설ID를 담는다. 하나라도 없으면 아무것도 바꾸지 않고 404다.

                    <b>응답 순서는 요청 순서와 다를 수 있다</b> — 조회 순서가 DB가 정하는 대로다. \
                    위치로 짝짓지 말고 시설ID로 맞춰야 한다.

                    요청 안에 같은 시설ID를 두 번 담으면 안 된다 — 중복 방어가 없어 500이 난다(05 「알려진 한계」 2).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 요청한 시설ID 중 없는 것이 있다")
    @PutMapping("/list")
    public ApiResponse<List<FcltResponse>> modifyFclts(@RequestBody @Valid FcltModifyListRequest request) {
        return ApiResponse.ok(fcltService.modifyFclts(request));
    }
}
