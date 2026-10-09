package com.mo.swtp.ems.ctrl.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * 제어그룹 일괄 등록 요청.
 *
 * <p>{@code @Valid}가 없으면 원소의 제약이 검사되지 않는다 — 컬렉션은 자동으로 캐스케이드되지 않는다.
 */
@Schema(description = "제어그룹 일괄 등록 요청")
public record CtrlGrpAddListRequest(

        @Schema(description = "등록할 제어그룹 목록. 전부 성공하거나 전부 실패한다")
        @NotEmpty
        @Valid
        List<CtrlGrpAddRequest> requests) {
}
