package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import com.mo.swtp.starter.kafka.event.SwtpEventPublisher;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 수집 측정값 → telemetry.raw 토픽 중계 (아키텍처 10장 수집 흐름의 "Kafka Producer" 단계).
 * AFTER_COMMIT: 자기 사본이 커밋 확정된 측정값만 발행한다.
 */
@Component
@RequiredArgsConstructor
public class SampleCollectorEventRelay {

    static final String TOPIC = "telemetry.raw";

    private final SwtpEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(SampleMeasurementCollectedEvent event) {
        // 파티션 키 = tagId → 같은 태그의 측정 순서 보장 (telemetry 적재·SSE와 동일 규약)
        eventPublisher.publish(TOPIC, event.measurement().tagId(),
                "SAMPLE_MEASUREMENT_COLLECTED", event.measurement());
    }
}
