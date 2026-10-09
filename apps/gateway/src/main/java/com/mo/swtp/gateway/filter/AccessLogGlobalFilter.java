package com.mo.swtp.gateway.filter;

import java.net.URI;

import com.mo.swtp.common.observability.SwtpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 게이트웨이 접근 로그 — 외부 진입점을 통과한 모든 요청의 라우팅 결과를 남긴다.
 *
 * <p>필드는 SLF4J fluent API의 key-value로 넣는다. 구조화 로깅(ECS)에서 개별 필드로 직렬화되므로
 * 로그 검색 백엔드에서 정규식 없이 조회할 수 있다.
 *
 * <p>{@code doFinally}로 기록하는 이유: SSE/WebSocket 같은 스트리밍 응답은 체인이 즉시 완료되지 않는다.
 * 종료 시그널(완료/에러/취소) 시점에 기록해야 실제 소요시간과 종료 사유가 정확히 남는다.
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("swtp.gateway.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.nanoTime();
        var request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getRawPath();

        return chain.filter(exchange).doFinally(signalType -> {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            var statusCode = exchange.getResponse().getStatusCode();
            // 지역변수로 받아 타입을 String으로 확정한다. exchange.getAttribute()는 <T> T라,
            // 인라인으로 넘기면 컴파일러가 addKeyValue(String, Supplier)를 골라 checkcast를 넣고
            // 런타임에 ClassCastException이 난다 — doFinally 안이라 Reactor가 삼켜서
            // "요청은 200인데 접근 로그만 조용히 사라지는" 형태로 나타난다.
            String requestId = exchange.getAttribute(SwtpHeaders.REQUEST_ID_MDC_KEY);

            log.atInfo()
                    .addKeyValue(SwtpHeaders.REQUEST_ID_MDC_KEY, requestId)
                    .addKeyValue("http.method", method)
                    .addKeyValue("http.path", path)
                    .addKeyValue("http.status", statusCode != null ? statusCode.value() : null)
                    .addKeyValue("gateway.route", routeId(exchange))
                    .addKeyValue("gateway.target", targetUri(exchange))
                    .addKeyValue("duration.ms", elapsedMs)
                    // 스트리밍 응답에서 정상 종료/클라이언트 취소를 구분하는 근거
                    .addKeyValue("signal", signalType.name())
                    .log("{} {} -> {} ({}ms)", method, path, statusCode, elapsedMs);
        });
    }

    /** 매칭된 라우트 ID — predicate 미매칭(404)이면 null */
    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : null;
    }

    /** 실제 전달된 백엔드 URI — 라우팅 오설정 진단의 핵심 정보 */
    private String targetUri(ServerWebExchange exchange) {
        URI uri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        return uri != null ? uri.toString() : null;
    }

    @Override
    public int getOrder() {
        // 요청 ID 발급 직후 — 이후 모든 처리 시간을 포함해 측정한다
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
