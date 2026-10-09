package com.mo.swtp.ems.ctrl.domain;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.starter.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제어그룹 태그 편성 — {@code ctrl_grp_tag_p}.
 *
 * <p>PK가 {@code tag_sn} 단독이다 — 태그 하나는 제어그룹 하나에만 속한다.
 * {@code master.eqp_tag_p}가 같은 형태이고, {@code V2__master_domain.sql}이 그것을
 * *"의도된 1:1 제약"* 이라고 명시해 뒀다.
 *
 * <p>{@code tag_sn}은 master 스키마가 소유한 키의 복제본이다. FK가 없고 <b>존재를 확인하는 장치도
 * 아직 없다</b>(문서 01 「한계 4」 승계).
 *
 * <p>{@code mdf_*}를 갖지만 delete-then-insert 경로에서는 항상 {@code rgstr_*}와 같은 값이 된다
 * (문서 03 「결정 4」).
 */
@Entity
@Table(name = "ctrl_grp_tag_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtrlGrpTag extends BaseEntity {

    @Id
    @Column(name = "tag_sn", nullable = false, length = ColLength.TAG_SN)
    private String tagSn;

    @Column(name = "ctrl_grp_id", nullable = false, length = ColLength.CTRL_GRP_ID)
    private String ctrlGrpId;

    /** 정렬순서 — 요청 배열의 순서로 1부터 매긴다 */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public CtrlGrpTag(String ctrlGrpId, String tagSn, Integer sortOrd) {
        this.ctrlGrpId = ctrlGrpId;
        this.tagSn = tagSn;
        this.sortOrd = sortOrd;
    }
}
