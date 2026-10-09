package com.mo.swtp.ems.ctrl.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이번 편성 저장으로 <b>다른 그룹에서 빠진</b> 구성원과 그 출처 그룹.
 *
 * <p>이 record가 존재하는 이유가 기록으로 남을 값이 있다. 정수장 도메인 관점은 뺏어오기를
 * <b>409로 거절</b>해야 한다고 봤다 — *"정수장에서 남의 계열 펌프가 소리 없이 빠지는 것은 사고다."*
 * 뺏어오기를 허용하기로 결정하면서(문서 03 「결정 2」) 그 우려가 사라진 것은 아니므로,
 * <b>"소리 없이"만이라도 없앤다.</b> 화면이 이 목록을 띄우면 운영자가 무엇을 건드렸는지 안다.
 */
@Schema(description = "이번 저장으로 다른 그룹에서 빠진 구성원")
public record CtrlGrpStolenMemberResponse(

        @Schema(description = "빠진 구성원의 식별자")
        String memberId,

        @Schema(description = "그 구성원이 원래 편성돼 있던 제어그룹ID")
        String fromCtrlGrpId) {
}
