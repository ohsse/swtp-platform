package com.mo.swtp.common.security;

/**
 * 플랫폼 JWT의 클레임 규약 — <b>발급(auth-service)과 검증(security-starter, gateway)의 유일한 합의점</b>.
 *
 * <p>게이트웨이는 WebFlux, 서비스는 서블릿이라 보안 스타터를 공유할 수 없다.
 * 그래서 발급·검증 양쪽이 참조할 수 있는 이 라이브러리에 상수를 둔다 —
 * 문자열을 각자 하드코딩하면 오타 하나로 "서명은 맞는데 권한이 비는" 조용한 실패가 난다.
 */
public final class SwtpJwtClaims {

    /** 토큰 용도 구분 — 액세스 토큰과 WebSocket 티켓이 서로 대체되지 않게 막는다 */
    public static final String TOKEN_TYPE = "typ";

    /** 사용자 로그인 ID (표시용). 식별자 자체는 표준 {@code sub} 클레임을 쓴다 */
    public static final String USERNAME = "username";

    /** 역할 목록 — 권한 접두사({@code ROLE_}) 없이 순수 역할명만 담는다 */
    public static final String ROLES = "roles";

    /** 액세스 토큰 — API 호출에 사용 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /**
     * WebSocket/SSE 핸드셰이크 전용 단기 티켓.
     *
     * <p>브라우저의 {@code EventSource}/{@code WebSocket}은 Authorization 헤더를 붙일 수 없어
     * 쿼리 파라미터로 자격을 넘겨야 하는데, 액세스 토큰을 그대로 넘기면 액세스 로그·Referer·
     * 프록시 캐시에 장수명 토큰이 남는다. 그래서 수십 초짜리 1회성 티켓을 따로 발급한다.
     */
    public static final String TOKEN_TYPE_WS_TICKET = "ws-ticket";

    private SwtpJwtClaims() {
    }
}
