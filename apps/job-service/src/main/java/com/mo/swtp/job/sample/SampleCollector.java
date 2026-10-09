package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data Collector — Job Service 내부 컴포넌트 (Phase 8 결정, 아키텍처 10장의 미결정 해소).
 * 실설계에서는 PLC/SCADA/외부API 어댑터가 들어올 자리 — 샘플은 고정 태그의 랜덤 값을 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleCollector {

    /** telemetry 쪽 테스트 태그(turbidity-*)와 충돌하지 않는 명명 */
    private static final List<String> TAG_IDS =
            List.of("sample-job-tag-a", "sample-job-tag-b", "sample-job-tag-c");

    private final SampleCollectedMeasurementRepository repository;
    private final ApplicationEventPublisher domainEventPublisher;

    /** 태그별 측정값 1건씩 수집 — 자기 사본 insert + 도메인 이벤트 (커밋 후 릴레이가 telemetry.raw 발행) */
    @Transactional
    public void collect() {
        Instant measuredAt = Instant.now();
        for (String tagId : TAG_IDS) {
            double value = Math.round(ThreadLocalRandom.current().nextDouble() * 1000.0) / 1000.0;
            SampleCollectedMeasurement measurement = new SampleCollectedMeasurement(tagId, value, measuredAt);
            repository.insert(measurement);
            domainEventPublisher.publishEvent(new SampleMeasurementCollectedEvent(measurement));
        }
        log.debug("샘플 수집 {}건 — measuredAt={}", TAG_IDS.size(), measuredAt);
    }
}
