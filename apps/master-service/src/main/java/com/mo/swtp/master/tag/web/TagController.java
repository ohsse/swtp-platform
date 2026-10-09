package com.mo.swtp.master.tag.web;


import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.mo.swtp.master.tag.dto.TagAddListRequest;
import com.mo.swtp.master.tag.dto.TagAddRequest;
import com.mo.swtp.master.tag.dto.TagModifyListRequest;
import com.mo.swtp.master.tag.dto.TagModifyRequest;
import com.mo.swtp.master.tag.dto.TagResponse;

import java.util.List;

/**
 * 태그 API.
 *
 * <p><b>swagger의 {@code @Tag}와 도메인 엔티티 {@code Tag}는 단순명이 충돌한다.</b> 이 클래스가
 * 엔티티를 직접 참조하지 않아 지금은 애노테이션 쪽을 그대로 import하면 되지만, 나중에 엔티티를
 * 들여오게 되면 <b>빈도가 낮은 쪽(애노테이션)을 FQN으로 미룬다</b>(apps/CLAUDE.md 「API 문서화」).
 *
 * <p><b>swagger의 {@code @ApiResponse}도 공용 응답 봉투 {@link ApiResponse}와 충돌하므로 FQN으로 쓴다.</b>
 * 봉투는 모든 핸들러의 반환 타입에 나오고 애노테이션은 도메인 고유 에러에만 붙는다.
 */
@Tag(name = "태그",
        description = "계측 태그 기준정보. 삭제 API가 없다 — 해제는 useYn=N 수정으로 표현한다")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/master/tag")
public class TagController {
    
    private final TagService tagService;
    
    @Operation(summary = "태그 목록 조회",
            description = """
                    결과가 없으면 빈 배열과 함께 200이다(404가 아니다). 신규 정수장은 태그 0건에서 출발하므로 \
                    "아직 없음"과 "조회 실패"를 구분할 수 있어야 한다.

                    <b>정렬 순서를 보장하지 않는다</b> — 태그에는 정렬순서 컬럼이 없고 조회에 order by가 없다. \
                    순서에 의미를 두지 말고, 화면에서 필요한 정렬은 프론트가 한다.""")
    @GetMapping
    public ApiResponse<List<TagResponse>> getTagList(
            @Parameter(description = "사용여부 필터. 생략하면 사용·미사용을 모두 반환한다")
            @RequestParam(required = false) UseYn useYn) {
        return ApiResponse.ok(tagService.getTagList(useYn));
    }
    
    @Operation(summary = "태그 단건 조회",
            description = "목록과 달리 대상이 없으면 404다 — 지목한 자원이 없는 것은 실제 오류다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 해당 태그시리얼번호가 없다")
    @GetMapping("/{tagSn}")
    public ApiResponse<TagResponse> getTagByTagSn(
            @Parameter(description = "태그시리얼번호") @PathVariable String tagSn) {
        return ApiResponse.ok(tagService.getTagById(tagSn));
    }
    
    @Operation(summary = "태그타입별 목록 조회",
            description = "목록이므로 해당 타입의 태그가 없어도 빈 배열과 함께 200이다(404가 아니다)")
    @GetMapping("/type/{tagTypeCd}")
    public ApiResponse<List<TagResponse>> getTagByTagTypeCd(
            @Parameter(description = "태그타입코드") @PathVariable String tagTypeCd) {
        return ApiResponse.ok(tagService.getTagByTagTypeCd(tagTypeCd));
    }
    
    @Operation(summary = "태그 등록",
            description = """
                    useYn을 생략하면 Y로 등록된다.

                    <b>이미 있는 태그시리얼번호로 호출하면 409가 아니라 기존 행이 조용히 수정된다.</b> \
                    등록과 수정을 구분해서 부르는 쪽이 안전하다 — 중복 방어는 아직 없다(05 「알려진 한계」 2).""")
    @PostMapping
    public ApiResponse<TagResponse> addTag(@RequestBody @Valid TagAddRequest request) {
        return ApiResponse.ok(tagService.addTag(request));
    }
    
    @Operation(summary = "태그 일괄 등록",
            description = """
                    전부 성공하거나 전부 실패한다 — 일부만 등록된 상태가 남지 않는다.

                    단건과 마찬가지로 이미 있는 태그시리얼번호는 조용히 수정된다. \
                    요청 안에 같은 태그시리얼번호를 두 번 담으면 뒤의 것만 남는다(05 「알려진 한계」 2).""")
    @PostMapping("/list")
    public ApiResponse<List<TagResponse>> addTagList(@RequestBody @Valid TagAddListRequest requests) {
        return ApiResponse.ok(tagService.addTags(requests));
    }
    
    @Operation(summary = "태그 단건 수정",
            description = """
                    부분 수정이다. 본문에서 생략하거나 null로 보낸 필드는 바뀌지 않는다.

                    <b>수정 대상은 본문의 tagSn이 정한다 — 경로의 {tagSn}은 현재 무시된다.</b> \
                    둘을 다르게 보내면 본문 쪽이 수정되므로 같은 값을 보내야 한다(05 「알려진 한계」 1).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 본문의 태그시리얼번호가 없다")
    @PutMapping("/{tagSn}")
    public ApiResponse<TagResponse> modifyTag(@RequestBody @Valid TagModifyRequest request) {
        return ApiResponse.ok(tagService.modifyTag(request));
    }
    
    @Operation(summary = "태그 일괄 수정",
            description = """
                    각 항목이 자기 태그시리얼번호를 담는다. 하나라도 없으면 아무것도 바꾸지 않고 404다.

                    <b>응답 순서는 요청 순서와 다를 수 있다</b> — 조회 순서가 DB가 정하는 대로다. \
                    위치로 짝짓지 말고 태그시리얼번호로 맞춰야 한다.

                    요청 안에 같은 태그시리얼번호를 두 번 담으면 안 된다 — 중복 방어가 없어 500이 난다(05 「알려진 한계」 2).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "COMMON-404 — 요청한 태그시리얼번호 중 없는 것이 있다")
    @PutMapping("/list")
    public ApiResponse<List<TagResponse>> modifyTags(@RequestBody @Valid TagModifyListRequest request) {
        return ApiResponse.ok(tagService.modifyTags(request));
    }
}
