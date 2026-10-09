package com.mo.swtp.job;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 8 수직 슬라이스 통합 검증 — 수동 실행(202) → 수집/집계 → 이력 전이 + kafka 발행.
 * 샘플 도메인 폐기 시 이 테스트의 시나리오만 실도메인으로 교체한다 (AbstractIntegrationTest는 유지).
 */
@TestPropertySource(properties = {
        // Quartz 배선은 기동으로 검증하되 발화는 막는다 — SCHEDULED 시나리오는 전용 테스트가 담당
        "swtp.sample-job.collect-cron=0 0 0 1 1 ? 2099",
        "swtp.sample-job.aggregate-cron=0 0 0 1 1 ? 2099",
        // 컨텍스트별 스케줄러 격리 — quartz 스키마를 공유해도 SCHED_NAME이 다르면 서로의 트리거를 잡지 않는다
        "spring.quartz.scheduler-name=sample-slice-test"
})
class SampleJobSliceIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_PATH = "/api/job/sample-executions";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("토큰 없는 직접 호출은 401, actuator는 열려 있다 — 게이트웨이 우회 방어(다중 방어)")
    void directCallWithoutTokenIsRejected() {
        ResponseEntity<String> api = callWithoutToken(BASE_PATH);
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JSON.readTree(api.getBody()).path("code").asString()).isEqualTo("COMMON-401");

        // compose healthcheck와 Prometheus 스크래핑은 토큰 없이 접근한다
        assertThat(callWithoutToken("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("수동 실행은 202+executionId를 즉시 반환하고, 수집 → telemetry.raw/job.event 발행이 완결된다")
    void manualCollectRunsEndToEnd() {
        try (var consumer = newConsumer()) {
            consumer.subscribe(List.of("telemetry.raw", "job.event"));

            // 1) 수동 실행 → 202 + STARTING/MANUAL 이력 즉시 반환 (아키텍처 8.6)
            ResponseEntity<String> response = rest.postForEntity(BASE_PATH,
                    jsonRequest("""
                            {"jobName": "sample-collect", "requestedBy": "tester"}
                            """), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            JsonNode body = JSON.readTree(response.getBody());
            // 응답 봉투는 code + data 2필드 규약
            assertThat(body.properties()).hasSize(2);
            JsonNode accepted = body.path("data");
            String executionId = accepted.path("executionId").asString();
            assertThat(executionId).isNotEmpty();
            assertThat(accepted.path("triggerType").asString()).isEqualTo("MANUAL");
            assertThat(accepted.path("requestedBy").asString()).isEqualTo("tester");

            // 2) 이력이 COMPLETED로 전이될 때까지 폴링 (비동기 실행)
            awaitStatus(executionId, "COMPLETED");

            // 3) 자기 사본 적재 — Collector 고정 태그 3건 이상 (스케줄 테스트가 먼저 돌았어도 성립)
            Integer collected = jdbcClient
                    .sql("SELECT count(*) FROM sample_collected_measurement")
                    .query(Integer.class)
                    .single();
            assertThat(collected).isGreaterThanOrEqualTo(3);

            // 4) telemetry.raw 봉투 3건 + job.event 상태 전이(STARTED → COMPLETED, 같은 키 → 순서 보장) 수신
            List<JsonNode> rawPayloads = new ArrayList<>();
            List<String> jobEventTypes = new ArrayList<>();
            Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
            while (Instant.now().isBefore(deadline)
                    && (rawPayloads.size() < 3 || jobEventTypes.size() < 2)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode envelope = JSON.readTree(record.value());
                    if ("telemetry.raw".equals(record.topic())
                            && envelope.path("payload").path("tagId").asString().startsWith("sample-job-tag-")) {
                        rawPayloads.add(envelope.path("payload"));
                    }
                    if ("job.event".equals(record.topic()) && executionId.equals(record.key())) {
                        jobEventTypes.add(envelope.path("eventType").asString());
                    }
                }
            }
            assertThat(rawPayloads).hasSizeGreaterThanOrEqualTo(3);
            // payload는 telemetry 계약(tagId/value/measuredAt)을 지킨다 — E2E 성립 조건
            assertThat(rawPayloads.get(0).path("value").isNumber()).isTrue();
            assertThat(rawPayloads.get(0).path("measuredAt").asString()).isNotEmpty();
            assertThat(jobEventTypes).containsExactly("SAMPLE_JOB_STARTED", "SAMPLE_JOB_COMPLETED");
        }
    }

    @Test
    @DisplayName("집계 Job은 10분 버킷 avg/min/max를 적재하고 Batch 메타 이력(batch 스키마)을 남긴다")
    void aggregateBatchProducesBucketsAndMeta() {
        // 1) 한 버킷(01:00~01:10)에 3건 + 다음 버킷에 1건 직접 적재 (전용 태그 — 다른 테스트와 격리)
        insertMeasurement("agg-tag-x", 1.0, Instant.parse("2026-08-12T01:01:00Z"));
        insertMeasurement("agg-tag-x", 2.0, Instant.parse("2026-08-12T01:04:00Z"));
        insertMeasurement("agg-tag-x", 3.0, Instant.parse("2026-08-12T01:09:00Z"));
        insertMeasurement("agg-tag-x", 9.0, Instant.parse("2026-08-12T01:11:00Z"));

        // 2) 수동 실행 → COMPLETED 폴링
        ResponseEntity<String> response = rest.postForEntity(BASE_PATH,
                jsonRequest("""
                        {"jobName": "sample-aggregate"}
                        """), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String executionId = JSON.readTree(response.getBody()).path("data").path("executionId").asString();
        awaitStatus(executionId, "COMPLETED");

        // 3) 01:00 버킷 검증 — avg/min/max/count
        double[] bucket = jdbcClient.sql("""
                        SELECT avg_value, min_value, max_value, sample_count
                        FROM sample_measurement_aggregate
                        WHERE tag_id = 'agg-tag-x' AND window_start = :windowStart
                        """)
                .param("windowStart", OffsetDateTime.parse("2026-08-12T01:00:00Z"))
                .query((rs, rowNum) -> new double[] {
                        rs.getDouble("avg_value"), rs.getDouble("min_value"),
                        rs.getDouble("max_value"), rs.getDouble("sample_count")})
                .single();
        assertThat(bucket[0]).isEqualTo(2.0);
        assertThat(bucket[1]).isEqualTo(1.0);
        assertThat(bucket[2]).isEqualTo(3.0);
        assertThat(bucket[3]).isEqualTo(3.0);

        // 4) Batch 메타 이력 — batch 스키마 접두(table-prefix) 배선의 최종 증명
        Integer batchCompleted = jdbcClient
                .sql("SELECT count(*) FROM batch.batch_job_execution WHERE status = 'COMPLETED'")
                .query(Integer.class)
                .single();
        assertThat(batchCompleted).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("등록되지 않은 job 실행 요청은 404 + COMMON-404로 응답한다")
    void unknownJobFollowsErrorContract() {
        ResponseEntity<String> response = rest.postForEntity(BASE_PATH,
                jsonRequest("""
                        {"jobName": "no-such-job"}
                        """), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.properties()).hasSize(2);
        assertThat(body.path("code").asString()).isEqualTo("COMMON-404");
        assertThat(body.path("data").asString()).contains("no-such-job");
    }

    @Test
    @DisplayName("Quartz JDBC store에 샘플 Job 2개가 등록된다 — Flyway V1 + tablePrefix 배선 확인")
    void quartzJobsAreRegisteredInJdbcStore() {
        Integer count = jdbcClient
                .sql("SELECT count(*) FROM quartz.qrtz_job_details WHERE sched_name = 'sample-slice-test'")
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(2);
    }

    /** 이력 단건 조회를 폴링해 기대 상태가 될 때까지 기다린다 — FAILED로 끝나면 사유와 함께 즉시 실패 */
    private void awaitStatus(String executionId, String expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            last = JSON.readTree(rest.getForEntity(BASE_PATH + "/" + executionId, String.class).getBody())
                    .path("data");
            String status = last.path("status").asString();
            if (expected.equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                fail("실행 실패 — failureReason: " + last.path("failureReason").asString());
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("상태 전이 대기 시간 초과 — 마지막 이력: " + last);
    }

    private void insertMeasurement(String tagId, double value, Instant measuredAt) {
        jdbcClient.sql("""
                        INSERT INTO sample_collected_measurement (measured_at, tag_id, value)
                        VALUES (:measuredAt, :tagId, :value)
                        """)
                .param("measuredAt", OffsetDateTime.ofInstant(measuredAt, java.time.ZoneOffset.UTC))
                .param("tagId", tagId)
                .param("value", value)
                .update();
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "slice-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
    }

    private HttpEntity<String> jsonRequest(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }
}
