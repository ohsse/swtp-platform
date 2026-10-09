package com.mo.swtp.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;

import com.mo.swtp.starter.kafka.event.EventEnvelope;
import com.mo.swtp.telemetry.sample.SampleMeasurement;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 7 수직 슬라이스 통합 검증 — telemetry.raw 소비 → hypertable 적재 → 조회 API.
 * 샘플 도메인 폐기 시 이 테스트의 시나리오만 실도메인으로 교체한다 (AbstractIntegrationTest는 유지).
 */
class SampleTelemetrySliceIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_PATH = "/api/telemetry/sample-measurements";
    private static final String TOPIC = "telemetry.raw";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("토큰 없는 직접 호출은 401, actuator는 열려 있다 — 게이트웨이 우회 방어(다중 방어)")
    void directCallWithoutTokenIsRejected() {
        ResponseEntity<String> api = callWithoutToken(BASE_PATH + "/sample-tag-a/latest");
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JSON.readTree(api.getBody()).path("code").asString()).isEqualTo("COMMON-401");

        // compose healthcheck와 Prometheus 스크래핑은 토큰 없이 접근한다
        assertThat(callWithoutToken("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("발행된 측정값이 적재되어 기간/현재값 조회와 일치하고, 재소비는 멱등하다")
    void ingestedMeasurementMatchesQueries() {
        Instant first = Instant.parse("2026-08-12T01:00:00Z");
        Instant second = first.plus(Duration.ofMinutes(1));

        // 1) 측정값 발행 → 컨슈머 적재 대기 (그룹/오프셋은 스타터 기본값: 앱 이름 + earliest)
        publish(new SampleMeasurement("sample-tag-a", 0.34, first));
        String periodUrl = BASE_PATH + "?tagId=sample-tag-a&from=" + first + "&to=" + second;
        JsonNode body = awaitData(periodUrl, data -> data.size() == 1);
        assertThat(body.path("code").asString()).isEqualTo("SUCCESS");
        // 응답 봉투는 code + data 2필드 규약
        assertThat(body.properties()).hasSize(2);
        assertThat(body.path("data").get(0).path("value").asDouble()).isEqualTo(0.34);
        assertThat(body.path("data").get(0).path("measuredAt").asString())
                .isEqualTo(first.toString());

        // 2) 동일 (tagId, measuredAt) 재발행(값만 다름) + 마커 발행 → 마커만 늘어난다 (ON CONFLICT 멱등)
        publish(new SampleMeasurement("sample-tag-a", 99.9, first));
        publish(new SampleMeasurement("sample-tag-a", 1.25, second));
        JsonNode data = awaitData(periodUrl, d -> d.size() == 2).path("data");
        assertThat(data.get(0).path("value").asDouble()).isEqualTo(0.34); // 원본 값 유지 — 덮어쓰지 않는다
        assertThat(data.get(1).path("value").asDouble()).isEqualTo(1.25);

        // 3) 현재값 조회 — 최신 측정(마커)이 반환된다
        JsonNode latest = JSON.readTree(
                rest.getForEntity(BASE_PATH + "/sample-tag-a/latest", String.class).getBody());
        assertThat(latest.path("data").path("measuredAt").asString()).isEqualTo(second.toString());
        assertThat(latest.path("data").path("value").asDouble()).isEqualTo(1.25);
    }

    @Test
    @DisplayName("측정값이 없는 태그의 현재값 조회는 404 + COMMON-404로 응답한다")
    void latestOfUnknownTagFollowsErrorContract() {
        ResponseEntity<String> response =
                rest.getForEntity(BASE_PATH + "/no-such-tag/latest", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("code").asString()).isEqualTo("COMMON-404");
        assertThat(body.path("data").asString()).contains("no-such-tag");
    }

    @Test
    @DisplayName("sample_measurement는 hypertable로 생성된다 — Flyway create_hypertable 실동작 확인")
    void tableIsHypertable() {
        Integer count = jdbcClient.sql("""
                        SELECT count(*)
                        FROM timescaledb_information.hypertables
                        WHERE hypertable_name = 'sample_measurement'
                        """)
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(1);
    }

    /** 봉투 JSON을 문자열로 발행한다 — CLI/Job Collector 발행과 동일한 계약(JSON 구조) 검증 */
    private void publish(SampleMeasurement measurement) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            String envelopeJson = JSON.writeValueAsString(
                    EventEnvelope.of("sample-measurement.recorded", "slice-test", measurement));
            producer.send(new ProducerRecord<>(TOPIC, measurement.tagId(), envelopeJson));
            producer.flush();
        }
    }

    /** 기간 조회를 폴링해 data가 조건을 만족할 때까지 기다린다 */
    private JsonNode awaitData(String url, Predicate<JsonNode> ready) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            last = JSON.readTree(rest.getForEntity(url, String.class).getBody());
            if (ready.test(last.path("data"))) {
                return last;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fail("적재 대기 시간 초과 — 마지막 응답: " + last);
    }
}
