package com.mo.swtp.job;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Quartz 자동 발화 검증 — cron을 5초로 재정의해 SCHEDULED 이력이 launcher 동일 경로로 남는지 확인한다.
 * 컨텍스트를 클래스 종료 시 폐기한다 — 5초 cron 스케줄러가 캐시에 살아남아 후속 테스트를 오염시키지 않도록.
 */
@TestPropertySource(properties = {
        "swtp.sample-job.collect-cron=*/5 * * * * ?",
        "swtp.sample-job.aggregate-cron=0 0 0 1 1 ? 2099",
        "spring.quartz.scheduler-name=sample-scheduled-test"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SampleScheduledJobIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("Quartz cron 발화가 SCHEDULED 이력으로 남고 완주한다 — 자동/수동 동일 경로")
    void scheduledCollectRunsViaSameLauncherPath() {
        // 이력 테이블은 컨테이너(DB) 공유라 반드시 triggerType으로 스코프해서 단언한다
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            last = JSON.readTree(rest.getForEntity(
                    "/api/job/sample-executions?jobName=sample-collect", String.class).getBody()).path("data");
            for (JsonNode row : last) {
                if ("SCHEDULED".equals(row.path("triggerType").asString())
                        && "COMPLETED".equals(row.path("status").asString())) {
                    return;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("SCHEDULED 완주 이력 대기 시간 초과 — 마지막 이력: " + last);
    }
}
