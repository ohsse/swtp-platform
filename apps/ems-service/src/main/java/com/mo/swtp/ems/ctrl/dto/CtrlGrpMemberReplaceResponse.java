package com.mo.swtp.ems.ctrl.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 편성 교체 결과 — 확정된 구성원 목록과, 그 과정에서 다른 그룹에서 빠진 목록.
 *
 * <p>{@code stolen}이 비어 있으면 이번 저장이 다른 그룹을 건드리지 않았다는 뜻이다.
 */
@Schema(description = "편성 교체 결과")
public record CtrlGrpMemberReplaceResponse(

        @Schema(description = "저장 후 확정된 편성 목록. 요청 배열 순서대로 정렬순서가 매겨져 있다")
        List<CtrlGrpMemberResponse> members,

        @Schema(description = """
                이번 저장으로 <b>다른 그룹에서 빠진</b> 구성원 목록. 비어 있으면 다른 그룹을 건드리지 않았다는 뜻이다. \
                화면은 이 목록을 운영자에게 보여 무엇이 움직였는지 알린다.""")
        List<CtrlGrpStolenMemberResponse> stolen) {
}
