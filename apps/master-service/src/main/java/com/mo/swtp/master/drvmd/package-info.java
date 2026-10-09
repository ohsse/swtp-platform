/**
 * 운전모드 변경이력 — {@code operation.drvmd_chg_h}의 <b>조회 전용</b> 슬라이스.
 *
 * <p>이 패키지에 write 경로가 없는 것은 누락이 아니라 계약이다. 이력 INSERT는 모드를 실제로 바꾸는
 * 서비스(ems·autonomous)가 자기 트랜잭션에서 직접 한다 — master가 대신 받아 쓰면 "모드는 바뀌었는데
 * 이력만 실패한" 창이 생기고 {@code iss_svc_cd}가 호출자가 넘기는 위조 가능한 값이 된다
 * ({@code docs/02-운전모드-변경이력-DDL.md} 결정 1).
 *
 * <p>같은 이유로 JPA 엔티티를 두지 않는다. {@code @Entity}가 없으면 {@code save()}도 없다
 * ({@code docs/04-운전모드-변경이력-조회.md} 결정 3).
 */
package com.mo.swtp.master.drvmd;
