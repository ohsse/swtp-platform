package com.mo.swtp.common.audit;

import java.util.Optional;

/**
 * "지금 이 작업을 하는 사람이 누구인가"를 알려주는 SPI — 감사 컬럼 {@code rgstr_id}/{@code mdf_id}의 값 공급원.
 *
 * <p>영속성 스타터는 Spring Data의 {@code AuditorAware} 빈을 <b>단독으로</b> 소유하고,
 * 실제 사용자 식별은 이 인터페이스 구현체에 런타임 위임한다. 이렇게 나눈 이유:
 *
 * <ul>
 *   <li>Spring Data는 {@code AuditingHandler}를 {@code AUTOWIRE_BY_TYPE}으로 배선한다 —
 *       컨텍스트에 {@code AuditorAware} 빈이 둘이면 기동이 깨진다. 스타터끼리
 *       {@code @ConditionalOnMissingBean}으로 경쟁시키면 결과가 자동구성 평가 순서에 좌우된다</li>
 *   <li>"누가 로그인했는가"는 보안 스타터의 관심사이고, "그 값을 어느 컬럼에 넣는가"는
 *       영속성 스타터의 관심사다. 이 인터페이스가 그 경계다</li>
 * </ul>
 *
 * <p>구현체를 빈으로 등록하면 자동으로 사용된다. 여러 개면 {@code @Order} 우선순위대로
 * 처음 값을 반환하는 구현체가 이긴다. 하나도 없거나 전부 비어 있으면 시스템 기본값이 쓰인다.
 */
@FunctionalInterface
public interface AuditorProvider {

    /**
     * 현재 감사 주체 식별자. 값이 없으면(비인증 요청, 배치 스레드 등) {@link Optional#empty()}.
     *
     * @return {@code rgstr_id}/{@code mdf_id}에 기록할 식별자 — 컬럼 길이(50) 이내여야 한다
     */
    Optional<String> currentAuditor();
}
