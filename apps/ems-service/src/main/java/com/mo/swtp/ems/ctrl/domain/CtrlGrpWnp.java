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
 * 제어그룹 수계통지점 편성 — {@code ctrl_grp_wnp_p}.
 *
 * <p>PK가 {@code wnp_id} 단독이다 — 지점 하나는 제어그룹 하나에만 속한다.
 *
 * <p><b>{@code ctrl} 도메인이 소유한다.</b> PK가 {@code wnp_id} 단독이라 소유 규칙 ②로 재면
 * {@code wnp}가 되지만, ①("그 관계를 만들고 끊는 API를 소유하는 도메인")에서 이미 갈린다 —
 * "제어그룹에 수계통지점을 편성한다"이므로 {@code ctrl}이다. <b>①이 먼저이므로 ②까지 가지 않는다.</b>
 *
 * <p>{@code wnpId}는 {@code String} 스칼라다 — {@code Wnp} 엔티티를 타입으로 참조하지 않는다
 * (apps/CLAUDE.md 「의존 방향」).
 *
 * <p><b>{@code mdf_*}를 갖지만 이 경로에서는 채워질 일이 없다.</b> 편성 저장이
 * delete-then-insert이므로 항상 {@code rgstr_*}와 같은 값이 된다 — 세 편성 테이블의 코드 경로를
 * 하나로 두기 위해 감수한 대가다(문서 03 「결정 4」, 「알려진 한계」).
 */
@Entity
@Table(name = "ctrl_grp_wnp_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtrlGrpWnp extends BaseEntity {

    @Id
    @Column(name = "wnp_id", nullable = false, length = ColLength.WNP_ID)
    private String wnpId;

    @Column(name = "ctrl_grp_id", nullable = false, length = ColLength.CTRL_GRP_ID)
    private String ctrlGrpId;

    /** 정렬순서 — 요청 배열의 순서로 1부터 매긴다 */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public CtrlGrpWnp(String ctrlGrpId, String wnpId, Integer sortOrd) {
        this.ctrlGrpId = ctrlGrpId;
        this.wnpId = wnpId;
        this.sortOrd = sortOrd;
    }
}
