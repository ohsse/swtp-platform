package com.mo.swtp.ems.wnp.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** 수계통지점 일괄 수정 요청 */
@Schema(description = "수계통지점 일괄 수정 요청")
public record WnpModifyListRequest(

        @Schema(description = "수정할 항목 목록. 응답은 이 순서 그대로 돌아온다")
        @NotEmpty
        @Valid
        List<WnpModifyItemRequest> requests) {
}
