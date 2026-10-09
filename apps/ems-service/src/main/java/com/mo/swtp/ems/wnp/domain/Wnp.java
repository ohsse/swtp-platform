package com.mo.swtp.ems.wnp.domain;

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
 * 수계통지점 — {@code wnp_m}.
 *
 * <p>펌프 제어의 결과로 물이 어느 분기로 가는지를 표현한다. 공정({@code master.prcs_m})·시설({@code master.fclt_m})과
 * 다른 축이며 EMS 제어 토폴로지의 일부라 ems가 소유한다(문서 01 「결정 1」).
 *
 * <p><b>지점 간 상하류 연결과 흐름 방향을 갖지 않는다.</b> 스키마에 그 컬럼이 없기 때문이다 —
 * "분기점"이라는 이름이 계통도를 연상시키지만 지금 만들면 반드시 틀린다.
 * 계통도 요구가 오면 ERD를 먼저 고친다.
 *
 * <p>제어그룹({@code ctrl_grp_m})을 타입으로 참조하지 않는다 — 둘의 연결은 편성 명세
 * {@code ctrl_grp_wnp_p}가 담당하고 그 테이블은 {@code ctrl} 도메인 소유다.
 */
@Entity
@Table(name = "wnp_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wnp extends BaseEntity {

    @Id
    @Column(name = "wnp_id", nullable = false, length = ColLength.WNP_ID)
    private String wnpId;

    @Column(name = "wnp_nm", nullable = false, length = ColLength.WNP_NM)
    private String wnpNm;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = ColLength.USE_YN)
    private UseYn useYn = UseYn.Y;

    /** 정렬순서 — nullable이므로 {@code int}로 받지 않는다(NULL 행 로드 시 예외) */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public Wnp(String wnpId, String wnpNm, UseYn useYn, Integer sortOrd) {
        this.wnpId = wnpId;
        this.wnpNm = wnpNm;
        // DDL의 DEFAULT 'Y'는 컬럼을 생략한 INSERT에만 걸린다 — JPA는 항상 값을 실으므로 여기서 채운다
        this.useYn = useYn == null ? UseYn.Y : useYn;
        this.sortOrd = sortOrd;
    }

    /** 부분 수정 — null인 필드는 건드리지 않는다 */
    public void replace(String wnpNm, UseYn useYn, Integer sortOrd) {
        if (wnpNm != null) {
            this.wnpNm = wnpNm;
        }
        if (useYn != null) {
            this.useYn = useYn;
        }
        if (sortOrd != null) {
            this.sortOrd = sortOrd;
        }
    }
}
