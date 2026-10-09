package com.mo.swtp.ems.ctrl.domain;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.starter.persistence.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제어설비 편성 — {@code ctrl_eqp_p}.
 *
 * <p><b>PK가 {@code eqp_id} 단독이다.</b> "설비 하나는 제어그룹 하나에만 속한다"는 뜻이고,
 * 이 제약이 편성 API의 모양을 거의 다 결정한다 — 그룹을 옮기는 것이 새 행 추가가 아니라
 * 기존 행의 이동이므로 개별 추가·삭제 API로는 안전하게 표현할 수 없다(문서 03 「결정 1·2」).
 *
 * <p><b>{@code mdf_*}가 없어 {@link BaseCreatedEntity}를 상속한다.</b> 그것이
 * "편성을 UPDATE 하지 않고 삭제 후 재등록한다"는 스키마의 선언이다({@code V1__ems_domain.sql}).
 * 따라서 {@code rgstr_*}는 "최초 편성"이 아니라 <b>"마지막으로 이 편성을 만든 사람과 시각"</b>이다.
 *
 * <p>{@code eqp_id}는 master 스키마가 소유한 키의 복제본이다. FK가 없고 <b>존재를 확인하는 장치도
 * 아직 없다</b>(문서 01 「한계 4」 승계). 설비명 같은 master 소유 속성을 여기 덧붙이지 않는다 —
 * 붙이는 순간 소유권이 흐려진다.
 */
@Entity
@Table(name = "ctrl_eqp_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtrlEqp extends BaseCreatedEntity {

    @Id
    @Column(name = "eqp_id", nullable = false, length = ColLength.EQP_ID)
    private String eqpId;

    @Column(name = "ctrl_grp_id", nullable = false, length = ColLength.CTRL_GRP_ID)
    private String ctrlGrpId;

    /** 정렬순서 — 요청 배열의 순서로 1부터 매긴다. 컬럼은 nullable이나 이 경로에서는 항상 값이 있다 */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public CtrlEqp(String ctrlGrpId, String eqpId, Integer sortOrd) {
        this.ctrlGrpId = ctrlGrpId;
        this.eqpId = eqpId;
        this.sortOrd = sortOrd;
    }
}
