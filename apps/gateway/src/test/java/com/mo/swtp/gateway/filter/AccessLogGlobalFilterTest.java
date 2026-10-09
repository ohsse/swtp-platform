package com.mo.swtp.gateway.filter;

import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.mo.swtp.common.observability.SwtpHeaders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접근 로그가 <b>실제로 남는지</b>를 검증한다.
 *
 * <p>순서(getOrder)만 검증하던 시절, {@code exchange.getAttribute()}의 제네릭 반환 타입 때문에
 * 컴파일러가 {@code addKeyValue(String, Supplier)} 오버로드를 골라 매 요청 ClassCastException이 났다.
 * 그 예외는 {@code doFinally} 안에서 Reactor가 삼켜(onErrorDropped) 요청은 200으로 끝나고
 * 접근 로그만 조용히 사라졌다 — 로그 출력 자체를 단언해야만 잡히는 결함이라 이 테스트를 둔다.
 */
class AccessLogGlobalFilterTest {

    private static final String ACCESS_LOGGER = "swtp.gateway.access";

    private final AccessLogGlobalFilter filter = new AccessLogGlobalFilter();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger accessLogger;

    @BeforeEach
    void attachAppender() {
        accessLogger = (Logger) LoggerFactory.getLogger(ACCESS_LOGGER);
        appender.start();
        accessLogger.addAppender(appender);
        accessLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        accessLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("요청마다 접근 로그 1건을 남기고 요청 ID를 구조화 필드로 싣는다")
    void writesAccessLogWithStructuredFields() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/master/sample-items"));
        exchange.getAttributes().put(SwtpHeaders.REQUEST_ID_MDC_KEY, "req-1");

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(appender.list).hasSize(1);
        // 라우팅 전 단계라 gateway.route/target은 null이다 — Collectors.toMap은 null 값을 거부하므로 직접 담는다
        Map<String, Object> fields = new HashMap<>();
        appender.list.getFirst().getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));

        // Supplier 오버로드가 잘못 선택되면 이 값이 문자열이 아니거나 로그 자체가 남지 않는다
        assertThat(fields).containsEntry(SwtpHeaders.REQUEST_ID_MDC_KEY, "req-1");
        assertThat(fields).containsEntry("http.method", "GET");
        assertThat(fields).containsEntry("http.path", "/api/master/sample-items");
        assertThat(fields).containsKeys("duration.ms", "signal");
    }

    @Test
    @DisplayName("요청 ID가 없어도 예외 없이 기록한다 — 로깅이 요청을 깨뜨리면 안 된다")
    void logsEvenWithoutRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(appender.list).hasSize(1);
    }
}
