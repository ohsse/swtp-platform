package com.mo.swtp.ems.ctrl.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** 제어그룹 일괄 수정 요청 */
@Schema(description = "제어그룹 일괄 수정 요청")
public record CtrlGrpModifyListRequest(

        @Schema(description = "수정할 항목 목록. 응답은 이 순서 그대로 돌아온다")
        @NotEmpty
        @Valid
        List<CtrlGrpModifyItemRequest> requests) {
}
