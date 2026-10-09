package com.mo.swtp.common.observability;

/**
 * 서비스 경계를 넘나드는 공통 헤더 이름.
 *
 * <p>게이트웨이(WebFlux)와 각 서비스(서블릿)가 같은 값을 써야 상관관계 추적이 성립하므로,
 * 웹 스택에 의존하지 않는 이 라이브러리에 둔다.
 */
public final class SwtpHeaders {

    /** 요청 단위 상관관계 ID — 게이트웨이가 발급하고 다운스트림 전 구간이 그대로 전파한다 */
    public static final String REQUEST_ID = "X-Request-Id";

    /** 로그 상관관계용 MDC 키 — 구조화 로그(ECS)에 그대로 실린다 */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * 게이트웨이가 JWT 검증 후 주입하는 사용자 식별자.
     *
     * <p><b>클라이언트가 보낸 동명 헤더는 게이트웨이가 무조건 제거한 뒤 자기 값으로 덮어쓴다.</b>
     * 그러지 않으면 헤더를 위조하는 것만으로 임의 사용자를 사칭할 수 있다.
     * 서비스는 이 헤더를 인증 근거로 신뢰하지 않는다 — 인증은 각자 JWT를 재검증해 수행하고,
     * 이 헤더는 로깅·감사·게이트웨이 우회 여부 판별 용도로만 쓴다.
     */
    public static final String USER_ID = "X-User-Id";

    /** 게이트웨이가 주입하는 역할 목록 (쉼표 구분). {@link #USER_ID}와 같은 위조 방지 규약을 따른다 */
    public static final String USER_ROLES = "X-User-Roles";

    /** WebSocket/SSE 핸드셰이크에서 Authorization 헤더 대신 쓰는 단기 티켓 쿼리 파라미터 */
    public static final String WS_TICKET_PARAM = "ticket";

    private SwtpHeaders() {
    }
}
