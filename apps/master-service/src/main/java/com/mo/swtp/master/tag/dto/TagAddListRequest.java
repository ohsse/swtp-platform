package com.mo.swtp.master.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 태그 일괄 등록 요청.
 *
 * <p>{@code @Valid}가 없으면 원소의 제약이 검사되지 않는다 — 컬렉션은 자동으로 캐스케이드되지 않는다.
 */
@Schema(description = "태그 일괄 등록 요청")
public record TagAddListRequest(

        @Schema(description = "등록할 태그 목록. 전부 성공하거나 전부 실패한다")
        @NotEmpty
        @Valid
        List<TagAddRequest> requests) {}
