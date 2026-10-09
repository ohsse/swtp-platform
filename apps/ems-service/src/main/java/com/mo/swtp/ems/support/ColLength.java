package com.mo.swtp.ems.support;

/**
 * 엔티티 {@code @Column(length=)}에 쓰는 컬럼 길이 상수.
 *
 * <p>진짜 SSOT는 {@code V1__ems_domain.sql}이다 — 이 클래스는 그 값을 자바 쪽에서 한 번만 적기 위한 것이지
 * 스키마를 정의하지 않는다. 둘이 어긋나도 {@code ddl-auto: none}이라 기동은 성공하므로,
 * 값을 바꿀 때는 반드시 마이그레이션 쪽을 먼저 본다.
 *
 * <p><b>값이 같아도 이름을 합치지 않는다.</b> master-service의 같은 클래스는
 * {@code EQP_ID}/{@code PRCS_ID}/{@code FCLT_ID}가 전부 36이라 상수를 잘못 골라도 아무도 모르는 상태다.
 * 여기서는 컬럼마다 이름을 따로 두어 잘못 고른 것이 이름으로 드러나게 한다.
 *
 * <p>앱 간 공유를 하지 않는 이유: 컬럼 길이는 각 서비스가 소유한 스키마의 성질이지 공용 계약이 아니다.
 * 루트 불변식 1이 앱 모듈 간 의존을 막고 있기도 하다.
 */
public final class ColLength {

    /** 제어그룹ID — ctrl_grp_m.ctrl_grp_id */
    public static final int CTRL_GRP_ID = 36;

    /** 제어그룹명 — ctrl_grp_m.ctrl_grp_nm */
    public static final int CTRL_GRP_NM = 50;

    /** 수계통지점ID — wnp_m.wnp_id */
    public static final int WNP_ID = 36;

    /** 수계통지점명 — wnp_m.wnp_nm */
    public static final int WNP_NM = 50;

    /** 사용여부 — CHARACTER(1). UseYn 상수 이름이 1글자인 것이 이 길이와 짝을 이룬다 */
    public static final int USE_YN = 1;

    /**
     * 설비ID — ctrl_eqp_p.eqp_id. <b>master 스키마가 소유한 키의 로컬 복제본이다.</b>
     * FK가 없고 스키마도 다르므로 이 길이는 master의 것을 따라 적은 값이지 여기서 정한 값이 아니다.
     */
    public static final int EQP_ID = 36;

    /** 태그시리얼번호 — ctrl_grp_tag_p.tag_sn. 이것도 master 소유 키의 복제본이다 */
    public static final int TAG_SN = 30;

    private ColLength() {
    }
}
