package com.mo.swtp.master.fclt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 시설 일괄 수정 요청 */
@Schema(description = "시설 일괄 수정 요청")
public record FcltModifyListRequest(

        @Schema(description = "수정할 항목 목록. 응답 순서는 이 순서와 다를 수 있으므로 시설ID로 짝지어야 한다")
        @NotEmpty
        @Valid
        List<FcltModifyRequest> requests) {}
