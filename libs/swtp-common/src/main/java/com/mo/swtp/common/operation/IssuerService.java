package com.mo.swtp.common.operation;

/**
 * 발행 서비스 ({@code operation.drvmd_chg_h.iss_svc_cd}) —
 * 운전모드를 <b>어느 서비스에서 변경했는가</b>.
 *
 * <p>운전모드 변경은 언제나 사람이 한다. 따라서 이 값은 "무엇이 발행했나"가 아니라
 * <b>"사람이 어느 서비스를 통해 바꿨나"</b>이고, {@code rgstr_id}(누가)와 짝을 이룬다.
 * 둘을 함께 봐야 책임 추적이 성립한다.
 *
 * <p><b>{@code MANUAL} 값을 두지 않는 이유</b>: 아키텍처 11.3이 정의한
 * {@code issuer_service = 'autonomous' | 'ems' | 'manual'} 3값은 <b>제어 명령</b> 이력의 것이다.
 * 거기서는 설비를 현장에서 직접 조작한 경우와 서비스를 거친 경우를 갈라야 하지만,
 * 운전모드에는 그런 경로가 없다 — 모드는 두 서비스의 화면에서만 바뀐다.
 * 쓰이지 않는 값을 두면 아무도 만들 수 없는 데이터를 위한 분기와 테스트가 남는다
 * ({@code SwtpAuthMode}가 외부 IdP 값을 두지 않은 것과 같은 판단이다).
 * 공통 HMI 같은 제3 경로가 실제로 생기면 그때 값을 더한다 — 빼는 것보다 더하는 쪽이 쉽다.
 *
 * <p><b>이 값은 각 서비스가 자기 트랜잭션에서 자기 코드를 박을 때만 신뢰할 수 있다.</b>
 * master가 이력 write API를 제공하고 각 서비스가 호출하는 구조였다면 호출자가 넘기는
 * 위조 가능한 파라미터가 된다. {@code operation} 스키마가 "DDL은 master / write는 공용"이라는
 * 특수 계약을 갖는 이유가 그것이다.
 *
 * <p>상수명이 곧 DB 코드값이며 컬럼은 {@code VARCHAR(10)}이다.
 *
 * @see ControlTargetType 대상 유형 — 지금은 이 값과 1:1이지만 축이 다르다
 */
public enum IssuerService {

    /** ems-service — 송수펌프·밸브를 제어그룹 단위로 다룬다(아키텍처 11.3의 제어 분담). */
    EMS,

    /** autonomous-service — 송수를 제외한 공정을 다룬다. */
    AUTO
}
