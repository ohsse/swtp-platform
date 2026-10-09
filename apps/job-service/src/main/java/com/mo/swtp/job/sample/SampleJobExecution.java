package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.Instant;
import java.util.UUID;

/**
 * Job 실행 이력 — 아키텍처 8.6(실행 상태)·8.7(Trigger Type) 패턴 검증용.
 * 상태 STOPPED, Trigger RETRY/RECOVERY는 이번 범위 외 (실설계에서 도입).
 */
public record SampleJobExecution(
        UUID executionId,
        String jobName,
        TriggerType triggerType,
        Status status,
        String requestedBy,
        Instant startedAt,
        Instant finishedAt,
        String failureReason) {

    /** 실행 유형 — 자동(Quartz)과 수동(REST)이 동일 경로를 타되 이력에서 구분된다 */
    public enum TriggerType { SCHEDULED, MANUAL }

    public enum Status { STARTING, RUNNING, COMPLETED, FAILED }
}
