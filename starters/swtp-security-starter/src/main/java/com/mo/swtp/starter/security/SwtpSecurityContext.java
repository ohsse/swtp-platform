package com.mo.swtp.starter.security;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * SecurityContext 조회 유틸리티 (아키텍처 15.1 "SecurityContext Utility").
 *
 * <p>서비스·리포지토리 계층처럼 컨트롤러 파라미터를 받을 수 없는 곳에서 현재 사용자를 조회한다.
 * 컨트롤러에서는 {@link CurrentUser}로 주입받는 편이 테스트하기 쉬우므로 그쪽을 우선한다.
 */
public final class SwtpSecurityContext {

    /** Spring Security 권한 표현의 접두사 — 도메인 역할명에는 포함되지 않는다 */
    private static final String ROLE_PREFIX = "ROLE_";

    private SwtpSecurityContext() {
    }

    /** 인증된 주체. 비인증 요청(permitAll 경로, 스케줄러 스레드 등)이면 비어 있다 */
    public static Optional<SwtpPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(SwtpPrincipal.from(jwt));
        }
        // JWT가 아닌 인증(@WithMockUser 등 테스트 경로) — 권한 목록에서 역할을 복원한다
        return Optional.of(new SwtpPrincipal(authentication.getName(), authentication.getName(),
                rolesOf(authentication)));
    }

    public static Optional<String> currentUserId() {
        return currentPrincipal().map(SwtpPrincipal::userId);
    }

    public static List<String> currentRoles() {
        return currentPrincipal().map(SwtpPrincipal::roles).orElseGet(List::of);
    }

    private static List<String> rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
