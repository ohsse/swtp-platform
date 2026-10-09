package com.mo.swtp.starter.security;

import java.util.Optional;

import com.mo.swtp.common.audit.AuditorProvider;

/**
 * 감사 컬럼({@code rgstr_id}/{@code mdf_id})에 현재 로그인 사용자를 공급한다.
 *
 * <p>영속성 스타터의 {@code AuditorAware}가 이 구현을 런타임 조회한다 —
 * 보안 스타터를 쓰지 않는 앱(배치 전용 등)에서는 구현체가 없어 시스템 기본값이 쓰인다.
 * 기록하는 값은 {@code sub}(사용자 식별자)이지 로그인 ID가 아니다 — ID는 바뀔 수 있지만
 * 식별자는 불변이라 이력 추적의 기준으로 삼을 수 있다.
 */
public class SecurityContextAuditorProvider implements AuditorProvider {

    @Override
    public Optional<String> currentAuditor() {
        return SwtpSecurityContext.currentUserId();
    }
}
