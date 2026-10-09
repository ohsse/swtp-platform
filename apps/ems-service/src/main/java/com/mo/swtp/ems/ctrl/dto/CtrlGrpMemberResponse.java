package com.mo.swtp.ems.ctrl.dto;

import java.time.LocalDateTime;

import com.mo.swtp.ems.ctrl.domain.CtrlEqp;
import com.mo.swtp.ems.ctrl.domain.CtrlGrpTag;
import com.mo.swtp.ems.ctrl.domain.CtrlGrpWnp;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 편성 구성원 한 건 — 설비·수계통지점·태그 <b>세 자원이 공유한다.</b>
 *
 * <p>세 편성 테이블이 구조적으로 동일하므로(대상ID + {@code sort_ord} + 등록 감사) record를 하나로 둔다.
 * 자원마다 쪼개면 필드명만 다른 쌍둥이가 셋 생기고, 세 편성 화면이 같은 컴포넌트를 쓸 수 없게 된다.
 *
 * <p><b>대가는 식별자 필드가 {@code eqpId}가 아니라 {@code memberId}라는 것이다.</b>
 * 요청 쪽은 자원별로 나눠 두었다({@code eqpIds}/{@code wnpIds}/{@code tagSns}) —
 * 보내는 값이 무엇인지는 프론트가 직접 조립하므로 필드명으로 드러나야 한다.
 *
 * <p>{@code mdf_*}를 싣지 않는다. 편성은 delete-then-insert라 수정 감사가 의미를 갖지 않는다 —
 * {@code ctrl_eqp_p}에는 컬럼 자체가 없고, 나머지 둘은 값이 항상 {@code rgstr_*}와 같다
 * (문서 03 「결정 4」). 02의 마스터 2종과 갈리는 지점이며, 그쪽은 실제 UPDATE가 일어나므로 싣는다.
 */
@Schema(description = "편성 구성원 한 건. 설비·수계통지점·태그 세 자원이 같은 형태를 쓴다")
public record CtrlGrpMemberResponse(

        @Schema(description = """
                구성원 식별자. 어느 편성을 조회했느냐에 따라 설비ID·수계통지점ID·태그시리얼번호 중 하나다 \
                — 세 자원이 이 타입을 공유하므로 자원별 이름을 쓰지 않는다.""")
        String memberId,

        @Schema(description = "정렬순서. 편성 저장 시 보낸 배열 순서대로 1부터 매겨진다", example = "1")
        Integer sortOrd,

        @Schema(description = "편성 등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "편성 등록자ID")
        String rgstrId) {

    public static CtrlGrpMemberResponse from(CtrlEqp ctrlEqp) {
        return new CtrlGrpMemberResponse(
                ctrlEqp.getEqpId(), ctrlEqp.getSortOrd(), ctrlEqp.getRgstrDttm(), ctrlEqp.getRgstrId());
    }

    public static CtrlGrpMemberResponse from(CtrlGrpWnp ctrlGrpWnp) {
        return new CtrlGrpMemberResponse(
                ctrlGrpWnp.getWnpId(), ctrlGrpWnp.getSortOrd(),
                ctrlGrpWnp.getRgstrDttm(), ctrlGrpWnp.getRgstrId());
    }

    public static CtrlGrpMemberResponse from(CtrlGrpTag ctrlGrpTag) {
        return new CtrlGrpMemberResponse(
                ctrlGrpTag.getTagSn(), ctrlGrpTag.getSortOrd(),
                ctrlGrpTag.getRgstrDttm(), ctrlGrpTag.getRgstrId());
    }
}
