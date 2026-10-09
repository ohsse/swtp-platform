package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import com.mo.swtp.starter.kafka.event.SwtpEventPublisher;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 실행 상태 전이 → job.event 토픽 중계 (아키텍처 9.2 "Job 완료 이벤트").
 * AFTER_COMMIT: 커밋이 확정된 전이만 발행한다 (롤백 시 미발행).
 *
 * <p>알려진 한계: 커밋 직후 프로세스가 죽으면 이벤트가 유실될 수 있다.
 * outbox 패턴은 실도메인 설계 시 재평가한다 (step-06 문서 참조).
 */
@Component
@RequiredArgsConstructor
public class SampleJobEventRelay {

    static final String TOPIC = "job.event";

    private final SwtpEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(SampleJobExecutionChangedEvent event) {
        // 파티션 키 = executionId → 같은 실행의 상태 전이 순서 보장
        eventPublisher.publish(TOPIC, event.execution().executionId().toString(),
                event.eventType(), event.execution());
    }
}
