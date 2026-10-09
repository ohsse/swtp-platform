package com.mo.swtp.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.mo.swtp.common.observability.SwtpHeaders;
import com.mo.swtp.common.security.SwtpAuthMode;
import com.mo.swtp.common.security.SwtpJwtClaims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;

/**
 * 게이트웨이 JWT 검증 + 사용자 헤더 주입 — 외부 진입점의 인증 관문.
 *
 * <p><b>Spring Security(WebFlux)를 얹지 않고 GlobalFilter로 구현한 이유</b>:
 * 게이트웨이는 자기 API가 없는 순수 라우터라 인가 규칙이 "경로 화이트리스트 + 토큰 검증" 뿐이다.
 * 여기에 SecurityWebFilterChain을 얹으면 인증 판단 지점이 시큐리티 체인과 게이트웨이 필터
 * 두 군데로 갈라져, 화이트리스트를 양쪽에 중복 관리해야 한다. 서명 검증이라는 어려운 부분은
 * {@link ReactiveJwtDecoder}(JWKS 캐싱·kid 선택·만료 검사 포함)에 그대로 위임한다.
 *
 * <p><b>다중 방어</b>: 여기서 통과시켰다고 서비스가 무조건 신뢰하지는 않는다. 각 서비스는
 * security-starter로 토큰을 한 번 더 검증한다 — 게이트웨이를 우회한 직접 호출을 막기 위해서다.
 * 같은 이유로 검증을 끄는 결정({@code swtp.auth.mode=none})도 게이트웨이 단독으로는 성립하지 않는다.
 * 스위치가 {@code config-repo}에 있어 양쪽이 같은 값을 받는다.
 */
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY = "{\"code\":\"COMMON-401\",\"data\":\"인증이 필요합니다\"}";
    private static final String UNAVAILABLE_BODY = "{\"code\":\"COMMON-503\",\"data\":\"인증 서비스를 사용할 수 없습니다\"}";

    private final ReactiveJwtDecoder jwtDecoder;
    private final SwtpAuthMode authMode;
    private final List<PathPattern> permitAllPatterns;
    private final List<PathPattern> ticketAuthPatterns;

    /**
     * @param jwtDecoder {@code authMode}가 검증 모드일 때만 필요하다. {@link SwtpAuthMode#NONE}이면 {@code null}
     */
    public JwtAuthGlobalFilter(ReactiveJwtDecoder jwtDecoder, SwtpAuthMode authMode,
            GatewaySecurityProperties properties) {
        // 검증 모드인데 검증기가 없으면 모든 요청이 조용히 통과한다 — 배선 실수를 기동 시점에 드러낸다
        Assert.isTrue(!authMode.verifies() || jwtDecoder != null,
                () -> "swtp.auth.mode=%s에는 ReactiveJwtDecoder가 필요하다".formatted(authMode));

        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.jwtDecoder = jwtDecoder;
        this.authMode = authMode;
        this.permitAllPatterns = properties.getPermitAllPaths().stream().map(parser::parse).toList();
        this.ticketAuthPatterns = properties.getTicketAuthPaths().stream().map(parser::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 클라이언트가 보낸 사용자 헤더는 인증 여부와 무관하게 항상 제거한다.
        // 화이트리스트 경로에서만 빠뜨려도 그 경로로 위조 헤더가 통과해 버린다.
        ServerWebExchange sanitized = stripClientSuppliedUserHeaders(exchange);

        // 검증하지 않는 모드에서도 위조 헤더 제거는 그대로 수행한다.
        // "인증을 안 한다"와 "클라이언트가 자기를 관리자라고 주장할 수 있다"는 전혀 다른 문제다 —
        // 다운스트림이 X-User-Id를 로깅·감사에 쓰므로 근거 없는 값이 흘러들면 기록이 오염된다.
        if (!authMode.verifies()) {
            return chain.filter(sanitized);
        }

        PathContainer path = sanitized.getRequest().getPath().pathWithinApplication();
        // CORS 사전 요청에는 Authorization 헤더가 실리지 않는다 — 인증을 요구하면 브라우저 호출이 전부 깨진다
        if (HttpMethod.OPTIONS.equals(sanitized.getRequest().getMethod()) || matches(permitAllPatterns, path)) {
            return chain.filter(sanitized);
        }

        String bearerToken = bearerToken(sanitized);
        if (bearerToken != null) {
            return authenticate(sanitized, chain, bearerToken, SwtpJwtClaims.TOKEN_TYPE_ACCESS);
        }

        // 헤더를 못 붙이는 스트리밍 핸드셰이크에 한해 단기 티켓을 받는다
        if (matches(ticketAuthPatterns, path)) {
            String ticket = sanitized.getRequest().getQueryParams().getFirst(SwtpHeaders.WS_TICKET_PARAM);
            if (ticket != null && !ticket.isBlank()) {
                return authenticate(sanitized, chain, ticket, SwtpJwtClaims.TOKEN_TYPE_WS_TICKET);
            }
        }
        return unauthorized(sanitized);
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain,
            String token, String requiredTokenType) {
        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    // 용도가 다른 토큰끼리 대체되지 않게 막는다 — 유출된 ws-ticket으로 일반 API를 호출하거나,
                    // 장수명 액세스 토큰을 쿼리 파라미터로 흘리는 두 방향 모두 차단된다.
                    if (!requiredTokenType.equals(jwt.getClaimAsString(SwtpJwtClaims.TOKEN_TYPE))) {
                        log.debug("토큰 용도 불일치 — 기대={}, 실제={}", requiredTokenType,
                                jwt.getClaimAsString(SwtpJwtClaims.TOKEN_TYPE));
                        return unauthorized(exchange);
                    }
                    return chain.filter(withUserHeaders(exchange, jwt));
                })
                .onErrorResume(error -> {
                    // 토큰이 틀린 것과 검증할 수가 없는 것을 응답 코드로 가른다.
                    // BadJwtException(서명 불일치·만료·형식 오류)만 클라이언트 잘못이라 401이고,
                    // 그 밖의 JwtException은 JWKS 조회 실패(auth-service 다운·네트워크 장애)다.
                    // 둘을 401로 뭉뚱그리면 인증 인프라 장애가 "토큰이 잘못됨"으로 위장돼,
                    // 클라이언트는 재로그인을 반복하고(그 /api/auth/login도 함께 죽어 있다)
                    // 운영자는 debug 로그를 켜기 전까지 원인을 볼 수 없다.
                    // 어느 쪽이든 사유 자체는 클라이언트에 알려주지 않는다.
                    if (error instanceof BadJwtException) {
                        log.debug("JWT 검증 실패: {}", error.getMessage());
                        return unauthorized(exchange);
                    }
                    log.warn("JWT를 검증할 수 없다 — JWKS 조회 실패로 보인다: {}", error.getMessage());
                    return serviceUnavailable(exchange);
                });
    }

    /**
     * 다운스트림이 쓸 사용자 정보를 헤더로 주입한다.
     *
     * <p>서비스가 이 헤더를 <b>인증 근거로</b> 쓰지는 않는다(각자 토큰을 재검증한다).
     * 로깅·감사·게이트웨이 경유 여부 판별용 부가 정보다.
     */
    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(SwtpJwtClaims.ROLES);
        return exchange.mutate()
                .request(builder -> {
                    builder.header(SwtpHeaders.USER_ID, jwt.getSubject());
                    builder.header(SwtpHeaders.USER_ROLES, roles == null ? "" : String.join(",", roles));
                })
                .build();
    }

    private static ServerWebExchange stripClientSuppliedUserHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (!headers.containsHeader(SwtpHeaders.USER_ID) && !headers.containsHeader(SwtpHeaders.USER_ROLES)) {
            return exchange;
        }
        return exchange.mutate()
                .request(builder -> builder.headers(mutable -> {
                    mutable.remove(SwtpHeaders.USER_ID);
                    mutable.remove(SwtpHeaders.USER_ROLES);
                }))
                .build();
    }

    private static boolean matches(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String bearerToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY);
    }

    /** 레지스트리에 인스턴스가 없을 때의 503과 같은 뜻 — 요청이 아니라 플랫폼 쪽 사정이다 */
    private static Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_BODY);
    }

    /** 서비스들의 전역 예외 규약(code + data 2필드)과 같은 봉투로 응답한다 */
    private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String body) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 접근 로그(HIGHEST+10) 다음 — 401로 끊긴 요청도 접근 로그에 남아야 한다
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
