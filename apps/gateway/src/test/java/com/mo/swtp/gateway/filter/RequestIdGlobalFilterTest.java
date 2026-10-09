package com.mo.swtp.gateway.filter;

import java.util.concurrent.atomic.AtomicReference;

import com.mo.swtp.common.observability.SwtpHeaders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdGlobalFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();

    /** 체인이 실제로 받은(=다운스트림으로 전달될) 요청을 포착한다 */
    private ServerHttpRequest captureDownstreamRequest(ServerWebExchange exchange) {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        filter.filter(exchange, downstream -> {
            captured.set(downstream.getRequest());
            return Mono.empty();
        }).block();
        return captured.get();
    }

    @Test
    @DisplayName("요청 ID가 없으면 발급해 다운스트림 요청과 응답 헤더에 싣는다")
    void generatesRequestIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items"));

        ServerHttpRequest downstream = captureDownstreamRequest(exchange);

        String forwarded = downstream.getHeaders().getFirst(SwtpHeaders.REQUEST_ID);
        assertThat(forwarded).as("다운스트림 전파").isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst(SwtpHeaders.REQUEST_ID))
                .as("호출자가 장애 문의 시 제시할 ID").isEqualTo(forwarded);
        assertThat(exchange.<String>getAttribute(SwtpHeaders.REQUEST_ID_MDC_KEY))
                .as("접근 로그 필터가 읽는 속성").isEqualTo(forwarded);
    }

    @Test
    @DisplayName("클라이언트가 보낸 요청 ID는 그대로 이어받는다 — 호출측 추적과 연결")
    void preservesIncomingRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(SwtpHeaders.REQUEST_ID, "caller-supplied-id"));

        ServerHttpRequest downstream = captureDownstreamRequest(exchange);

        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.REQUEST_ID))
                .isEqualTo("caller-supplied-id");
        assertThat(exchange.getResponse().getHeaders().getFirst(SwtpHeaders.REQUEST_ID))
                .isEqualTo("caller-supplied-id");
    }

    @Test
    @DisplayName("빈 문자열 요청 ID는 무시하고 새로 발급한다")
    void regeneratesBlankRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items")
                        .header(SwtpHeaders.REQUEST_ID, "   "));

        ServerHttpRequest downstream = captureDownstreamRequest(exchange);

        assertThat(downstream.getHeaders().getFirst(SwtpHeaders.REQUEST_ID)).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    @DisplayName("모든 필터보다 먼저 동작한다 — 이후 로그가 ID를 갖기 위한 전제")
    void runsFirst() {
        assertThat(filter.getOrder()).isLessThan(new AccessLogGlobalFilter().getOrder());
    }
}
