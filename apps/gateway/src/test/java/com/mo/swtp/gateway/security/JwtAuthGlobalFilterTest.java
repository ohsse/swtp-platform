package com.mo.swtp.gateway.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.mo.swtp.common.observability.SwtpHeaders;
import com.mo.swtp.common.security.SwtpAuthMode;
import com.mo.swtp.common.security.SwtpJwtClaims;
import com.mo.swtp.gateway.filter.AccessLogGlobalFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 게이트웨이 인증 관문 단위 검증.
 *
 * <p>서명 검증 자체는 {@link ReactiveJwtDecoder}(Nimbus)의 책임이므로 스텁으로 대체하고,
 * 이 필터가 책임지는 <b>경로 판단·토큰 용도 판별·헤더 위조 차단</b>만 검증한다.
 */
class JwtAuthGlobalFilterTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String WS_TICKET = "ws-ticket-token";

    /**
     * 토큰 문자열 → 검증 결과. 등록되지 않은 값은 서명 실패로 취급한다.
     *
     * <p>실패를 {@link BadJwtException}으로 던지는 것이 Nimbus의 실제 동작이다 — 평범한
     * {@link JwtException}은 "토큰이 틀렸다"가 아니라 "디코딩 자체를 못 했다"(JWKS 조회 실패)를 뜻하고,
     * 필터가 그 둘을 401/503으로 가르므로 스텁도 같은 구분을 지켜야 한다.
     */
    private static final ReactiveJwtDecoder DECODER = token -> {
        Map<String, Jwt> known = Map.of(
                ACCESS_TOKEN, jwt(ACCESS_TOKEN, SwtpJwtClaims.TOKEN_TYPE_ACCESS),
                WS_TICKET, jwt(WS_TICKET, SwtpJwtClaims.TOKEN_TYPE_WS_TICKET));
        Jwt found = known.get(token);
        return (found != null) ? Mono.just(found) : Mono.error(new BadJwtException("서명 검증 실패"));
    };

    private final JwtAuthGlobalFilter filter =
            new JwtAuthGlobalFilter(DECODER, SwtpAuthMode.INTERNAL, properties());

    @Test
    @DisplayName("화이트리스트 경로는 토큰 없이 통과한다 — 로그인 자체가 막히면 안 된다")
    void permitAllPathPassesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth-service/api/auth/login"));

        assertThat(downstreamRequest(exchange)).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("토큰이 없으면 401 + 공통 봉투로 끊는다")
    void missingTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items"));

        assertThat(downstreamRequest(exchange)).as("다운스트림으로 전달되지 않아야 한다").isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("COMMON-401");
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰도 401 — 실패 사유는 응답에 드러내지 않는다")
    void invalidTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer 위조된토큰"));

        assertThat(downstreamRequest(exchange)).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JWKS를 조회하지 못하면 401이 아니라 503 — 인프라 장애가 토큰 오류로 위장되면 안 된다")
    void jwksFailureIsServiceUnavailable() {
        // auth-service 다운·네트워크 장애: Nimbus는 이때 BadJwtException이 아닌 JwtException을 낸다
        ReactiveJwtDecoder unreachable = token -> Mono.error(new JwtException("JWKS 조회 실패"));
        var filterWithUnreachableJwks =
                new JwtAuthGlobalFilter(unreachable, SwtpAuthMode.INTERNAL, properties());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN));

        assertThat(downstreamRequest(filterWithUnreachableJwks, exchange)).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("COMMON-503");
    }

    @Test
    @DisplayName("유효 토큰이면 사용자 헤더를 주입해 전달한다")
    void injectsUserHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN));

        ServerHttpRequest downstream = downstreamRequest(exchange);

        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ID)).isEqualTo("u-1");
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ROLES)).isEqualTo("ADMIN,OPERATOR");
    }

    @Test
    @DisplayName("클라이언트가 위조한 사용자 헤더는 게이트웨이 값으로 덮어쓴다")
    void overwritesSpoofedUserHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(SwtpHeaders.USER_ID, "관리자사칭")
                        .header(SwtpHeaders.USER_ROLES, "ADMIN,SUPERUSER"));

        ServerHttpRequest downstream = downstreamRequest(exchange);

        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeaders().get(SwtpHeaders.USER_ID)).containsExactly("u-1");
        assertThat(downstream.getHeaders().get(SwtpHeaders.USER_ROLES)).containsExactly("ADMIN,OPERATOR");
    }

    @Test
    @DisplayName("화이트리스트 경로에서도 위조 헤더는 제거된다 — 인증을 건너뛰는 경로가 우회로가 되면 안 된다")
    void stripsSpoofedHeadersEvenOnPermitAllPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth-service/api/auth/login")
                        .header(SwtpHeaders.USER_ID, "관리자사칭"));

        ServerHttpRequest downstream = downstreamRequest(exchange);

        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ID)).isNull();
    }

    @Test
    @DisplayName("WebSocket 티켓을 Authorization 헤더로 보내면 거부한다 — 용도가 다른 토큰의 대체 사용 차단")
    void wsTicketCannotBeUsedAsAccessToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WS_TICKET));

        assertThat(downstreamRequest(exchange)).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("티켓 쿼리 파라미터는 실시간 경로에서만, ws-ticket 용도일 때만 통한다")
    void ticketParamWorksOnlyOnStreamingPaths() {
        MockServerWebExchange stream = MockServerWebExchange.from(
                MockServerHttpRequest.get("/realtime-service/api/realtime/stream?ticket=" + WS_TICKET));
        ServerHttpRequest downstream = downstreamRequest(stream);
        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ID)).isEqualTo("u-1");

        // 일반 API 경로에서는 쿼리 파라미터 티켓을 아예 보지 않는다
        MockServerWebExchange api = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items?ticket=" + WS_TICKET));
        assertThat(downstreamRequest(api)).isNull();

        // 실시간 경로라도 액세스 토큰을 쿼리 파라미터로 넘기는 것은 막는다 (로그 유출 방지)
        MockServerWebExchange wrongType = MockServerWebExchange.from(
                MockServerHttpRequest.get("/realtime-service/api/realtime/stream?ticket=" + ACCESS_TOKEN));
        assertThat(downstreamRequest(wrongType)).isNull();
    }

    @Test
    @DisplayName("CORS 사전 요청(OPTIONS)은 인증을 요구하지 않는다 — 브라우저는 Authorization을 붙이지 않는다")
    void preflightPasses() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/master/sample-items"));

        assertThat(downstreamRequest(exchange)).isNotNull();
    }

    @Test
    @DisplayName("접근 로그 다음에 동작한다 — 401로 끊긴 요청도 로그에 남아야 한다")
    void runsAfterAccessLog() {
        assertThat(filter.getOrder()).isGreaterThan(new AccessLogGlobalFilter().getOrder());
    }

    @Test
    @DisplayName("mode=none이면 토큰 없이도 통과시킨다 — 인증 체계가 없는 정수장 배포")
    void noneModePassesWithoutToken() {
        var openFilter = new JwtAuthGlobalFilter(null, SwtpAuthMode.NONE, properties());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items"));

        assertThat(downstreamRequest(openFilter, exchange)).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("mode=none에서도 위조 사용자 헤더는 제거한다 — 인증을 안 하는 것과 사칭을 허용하는 것은 다르다")
    void noneModeStillStripsSpoofedHeaders() {
        var openFilter = new JwtAuthGlobalFilter(null, SwtpAuthMode.NONE, properties());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(SwtpHeaders.USER_ID, "관리자사칭")
                        .header(SwtpHeaders.USER_ROLES, "ADMIN"));

        ServerHttpRequest downstream = downstreamRequest(openFilter, exchange);

        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ID)).isNull();
        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.USER_ROLES)).isNull();
    }

    @Test
    @DisplayName("검증 모드인데 검증기가 없으면 기동 시점에 끊는다 — 조용히 전부 통과하는 것이 최악이다")
    void verifyingModeRequiresDecoder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtAuthGlobalFilter(null, SwtpAuthMode.INTERNAL, properties()))
                .withMessageContaining("INTERNAL");
    }

    /** 체인이 실제로 받은 요청. 401로 끊기면 체인이 호출되지 않으므로 null이 된다 */
    private ServerHttpRequest downstreamRequest(ServerWebExchange exchange) {
        return downstreamRequest(filter, exchange);
    }

    private static ServerHttpRequest downstreamRequest(JwtAuthGlobalFilter filter, ServerWebExchange exchange) {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        filter.filter(exchange, downstream -> {
            captured.set(downstream.getRequest());
            return Mono.empty();
        }).block();
        return captured.get();
    }

    private static Jwt jwt(String tokenValue, String tokenType) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("u-1")
                .claim(SwtpJwtClaims.USERNAME, "admin")
                .claim(SwtpJwtClaims.ROLES, List.of("ADMIN", "OPERATOR"))
                .claim(SwtpJwtClaims.TOKEN_TYPE, tokenType)
                .build();
    }

    private static GatewaySecurityProperties properties() {
        var properties = new GatewaySecurityProperties();
        properties.setPermitAllPaths(List.of("/actuator/**", "/auth-service/api/auth/login", "/auth-service/api/auth/.well-known/**"));
        properties.setTicketAuthPaths(List.of("/realtime-service/**"));
        return properties;
    }
}
