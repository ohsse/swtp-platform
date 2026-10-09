package com.mo.swtp.common.operation;

/**
 * 제어대상 유형 ({@code operation.drvmd_chg_h.ctrl_trgt_type_cd}) —
 * 같은 행의 {@code ctrl_trgt_id}를 어느 테이블로 해석할지 정하는 판별자다.
 *
 * <p>{@code ctrl_trgt_id}가 다형 참조라 이 값 없이는 조인 대상이 정해지지 않는다.
 *
 * <p><b>지금은 {@link IssuerService}와 값이 1:1이다</b>({@code EMS}↔{@link #CTRL_GRP},
 * {@code AUTO}↔{@link #PRCS}). 그래도 별도 컬럼으로 두는 이유는 축이 다르기 때문이다 —
 * {@code iss_svc_cd}는 <i>책임 추적</i>(어느 서비스에서 바꿨나), 이 값은 <i>참조 해석</i>(무엇을 가리키나)이다.
 *
 * <p>실질적인 이유는 이것이다: 조회 코드가 {@code iss_svc_cd = 'EMS'}로 조인 대상을 추론하기
 * 시작하면, EMS가 공정 단위 모드를 다루게 되거나 자율운영이 제어그룹을 다루게 되는 순간
 * <b>조용히 깨진다.</b> 컴파일도 통과하고 쿼리도 성립하며 결과만 틀린다.
 * 게다가 그 시점에 <b>이미 쌓인 이력은 소급 해석이 불가능하다</b> — 발행자로부터 대상 종류를
 * 되돌릴 수 없기 때문이다. 비용은 {@code VARCHAR(10)} 한 칸이고, 위험은 되돌릴 수 없다.
 *
 * <p>이 프로젝트는 FK를 걸지 않으므로(참조 정합성은 애플리케이션 책임) DB가 잡아주지 않는다.
 * 컬럼 자체가 자기설명적이어야 하는 이유이기도 하다.
 *
 * <p>상수명이 곧 DB 코드값이며 컬럼은 {@code VARCHAR(10)}이다.
 */
public enum ControlTargetType {

    /**
     * 제어그룹 — {@code ems.ctrl_grp_m.ctrl_grp_id}를 가리킨다. EMS가 송수펌프를 묶는 단위다.
     *
     * <p>스키마가 다르다는 점이 설계에 영향을 준다: master는 이력 조회를 제공하면서
     * {@code ems} 스키마를 읽을 수 없으므로(소유권 원칙), 이력 행에 명칭 스냅샷
     * {@code ctrl_trgt_nm}을 함께 남긴다.
     */
    CTRL_GRP,

    /** 공정 — {@code master.prcs_m.prcs_id}를 가리킨다. 자율운영이 다루는 단위다. */
    PRCS
}
