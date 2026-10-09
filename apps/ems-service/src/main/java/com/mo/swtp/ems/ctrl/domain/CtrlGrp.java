package com.mo.swtp.ems.ctrl.domain;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.starter.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제어그룹 — {@code ctrl_grp_m}.
 *
 * <p>송수펌프 몇 대를 한 묶음으로 돌릴 것인가를 표현하는 운전 편성 단위다.
 * 편성을 바꾸는 주체는 운영자이며, EMS는 그 편성 위에서의 운전 상태를 다룬다.
 *
 * <p>PK가 애플리케이션이 넣는 assigned-ID라 {@code save()}가 {@code merge()} 경로를 탄다 —
 * 기존 ID로 등록하면 예외 없이 UPDATE가 되므로 서비스 계층이 사전 존재 검사를 책임진다
 * ({@link com.mo.swtp.ems.support.EmsErrorCode#DUPLICATE_ID}).
 *
 * <p>다른 도메인 엔티티를 타입으로 참조하지 않는다 — 편성 명세의 {@code eqp_id}·{@code tag_sn}은
 * master 소유 키의 복제본이고 FK 제약도 없다(apps/CLAUDE.md 「의존 방향」).
 */
@Entity
@Table(name = "ctrl_grp_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtrlGrp extends BaseEntity {

    @Id
    @Column(name = "ctrl_grp_id", nullable = false, length = ColLength.CTRL_GRP_ID)
    private String ctrlGrpId;

    @Column(name = "ctrl_grp_nm", nullable = false, length = ColLength.CTRL_GRP_NM)
    private String ctrlGrpNm;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = ColLength.USE_YN)
    private UseYn useYn = UseYn.Y;

    /** 정렬순서 — nullable이므로 {@code int}로 받지 않는다(NULL 행 로드 시 예외) */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public CtrlGrp(String ctrlGrpId, String ctrlGrpNm, UseYn useYn, Integer sortOrd) {
        this.ctrlGrpId = ctrlGrpId;
        this.ctrlGrpNm = ctrlGrpNm;
        // DDL의 DEFAULT 'Y'는 컬럼을 생략한 INSERT에만 걸린다 — JPA는 항상 값을 실으므로 여기서 채운다
        this.useYn = useYn == null ? UseYn.Y : useYn;
        this.sortOrd = sortOrd;
    }

    /** 부분 수정 — null인 필드는 건드리지 않는다 */
    public void replace(String ctrlGrpNm, UseYn useYn, Integer sortOrd) {
        if (ctrlGrpNm != null) {
            this.ctrlGrpNm = ctrlGrpNm;
        }
        if (useYn != null) {
            this.useYn = useYn;
        }
        if (sortOrd != null) {
            this.sortOrd = sortOrd;
        }
    }
}
