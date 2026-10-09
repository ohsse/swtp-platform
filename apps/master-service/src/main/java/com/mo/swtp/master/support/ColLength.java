package com.mo.swtp.master.support;


/**
 * 컬럼 길이 상수.
 *
 * <p>값의 SSOT는 이 파일이 아니라 {@code V2__master_domain.sql}이다 — 여기 있는 것은 그 DDL의 사본이고,
 * 엔티티의 {@code @Column(length=)}와 DTO의 {@code @Size(max=)}가 <b>같은 값을 보게 만드는 것</b>이 목적이다.
 * 한쪽만 고치면 검증을 통과한 값이 DB에서 잘린다.
 *
 * <p><b>도메인별로 쪼개지 않는다</b> — {@code tag/domain/EqpTag}가 {@code EQP_ID}를 쓰므로
 * 쪼개면 도메인 간 참조 금지 규칙에 걸려 순환이 된다(master-service/CLAUDE.md).
 */
public final class ColLength {


    /** ■■■■■■■■■■■■■■■■   공통   ■■■■■■■■■■■■■■■■*/
    public final static int USE_YN = 1;

    /** ■■■■■■■■■■■■■■■■   TAG   ■■■■■■■■■■■■■■■■*/
    public final static int TAG_SN = 30;
    public final static int TAG_TYPE_CD = 3;

    /** ■■■■■■■■■■■■■■■■   FCLT   ■■■■■■■■■■■■■■■■*/
    public final static int FCLT_ID = 36;
    public final static int FCLT_NM = 50;
    public final static int FCLT_TYPE_CD = 20;

    /** ■■■■■■■■■■■■■■■■   EQP   ■■■■■■■■■■■■■■■■*/
    public final static int EQP_ID = 36;
    public final static int EQP_NM = 50;
    public final static int EQP_TYPE_CD = 20;

    /** ■■■■■■■■■■■■■■■■   PRCS   ■■■■■■■■■■■■■■■■*/
    public final static int PRCS_ID = 36;
    public final static int PRCS_NM = 50;
    public final static int PRCS_TYPE_CD = 20;


    private ColLength() {
    }
}
