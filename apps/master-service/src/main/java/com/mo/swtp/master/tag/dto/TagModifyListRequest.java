package com.mo.swtp.master.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 태그 일괄 수정 요청 */
@Schema(description = "태그 일괄 수정 요청")
public record TagModifyListRequest(

        @Schema(description = "수정할 항목 목록. 응답 순서는 이 순서와 다를 수 있으므로 태그시리얼번호로 짝지어야 한다")
        @NotEmpty
        @Valid
        List<TagModifyRequest> requests) {}
