package com.mo.swtp.gateway.filter;

import java.util.UUID;

import com.mo.swtp.common.observability.SwtpHeaders;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 요청 상관관계 ID 발급/전파 — 모든 필터보다 먼저 동작해야 이후 로그가 ID를 갖는다.
 *
 * <p>클라이언트가 보낸 {@code X-Request-Id}는 그대로 신뢰해 이어받는다(호출측 추적 연결).
 * 없으면 게이트웨이가 새로 발급한다. 응답 헤더에도 실어 호출자가 장애 문의 시 ID를 제시할 수 있게 한다.
 *
 * <p>주의: 서비스 간 내부 호출은 게이트웨이를 거치지 않는다(아키텍처 3.1.3)는 원칙 때문에,
 * 이 필터만으로는 전 구간을 덮지 못한다. 서비스 간 직접 호출 시 헤더 릴레이가 별도로 필요하다.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(SwtpHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String resolved = requestId;
        // 라우팅 이전에 끝나는 응답(401, 404 등)에도 ID가 실리도록 먼저 세팅한다
        exchange.getResponse().getHeaders().set(SwtpHeaders.REQUEST_ID, resolved);
        // 다운스트림 서비스도 같은 헤더를 응답에 실어 보내고, 게이트웨이가 그 헤더를 그대로 복사하므로
        // 그냥 두면 응답에 X-Request-Id가 두 번 실린다. 커밋 직전에 set()으로 한 값만 남긴다.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(SwtpHeaders.REQUEST_ID, resolved);
            return Mono.empty();
        });

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.header(SwtpHeaders.REQUEST_ID, resolved))
                .build();
        // 접근 로그 필터가 같은 값을 읽도록 exchange 속성으로도 남긴다
        mutated.getAttributes().put(SwtpHeaders.REQUEST_ID_MDC_KEY, resolved);

        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
