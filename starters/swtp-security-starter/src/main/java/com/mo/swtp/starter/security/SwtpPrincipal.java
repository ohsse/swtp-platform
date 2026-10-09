package com.mo.swtp.starter.security;

import java.util.List;
import java.util.Set;

import com.mo.swtp.common.security.SwtpJwtClaims;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 검증된 토큰에서 뽑아낸 인증 주체 — 컨트롤러가 보는 사용자 표현.
 *
 * <p>{@code Jwt} 객체를 컨트롤러까지 흘리지 않는 이유: 토큰 포맷(클레임 이름, 서명 방식)이
 * 업무 코드에 스며들면 외부 IdP로 교체할 때 컨트롤러까지 고쳐야 한다. 이 record가 그 방화벽이다.
 *
 * @param userId   사용자 식별자 (표준 {@code sub} 클레임)
 * @param username 로그인 ID — 표시·감사 로그용
 * @param roles    역할 목록. {@code ROLE_} 접두사 없는 순수 역할명
 */
public record SwtpPrincipal(String userId, String username, List<String> roles) {

    public SwtpPrincipal {
        roles = (roles == null) ? List.of() : List.copyOf(roles);
    }

    /** 검증이 끝난 JWT에서 주체를 복원한다 */
    public static SwtpPrincipal from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(SwtpJwtClaims.ROLES);
        return new SwtpPrincipal(jwt.getSubject(),
                jwt.getClaimAsString(SwtpJwtClaims.USERNAME),
                roles == null ? List.of() : roles);
    }

    /** {@code ROLE_} 접두사 없이 비교한다 — 접두사는 Spring Security 권한 표현의 관례일 뿐 도메인 개념이 아니다 */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        return Set.of(candidates).stream().anyMatch(this::hasRole);
    }
}
