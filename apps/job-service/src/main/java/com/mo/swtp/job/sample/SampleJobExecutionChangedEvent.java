package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

/**
 * 실행 상태 전이 도메인 이벤트 — 트랜잭션 안에서 발행되고, 릴레이가 커밋 후 job.event 토픽으로 중계한다.
 * RUNNING 전이는 발행하지 않는다 (시작/종결만 외부 관심사).
 */
public record SampleJobExecutionChangedEvent(String eventType, SampleJobExecution execution) {

    public static final String STARTED = "SAMPLE_JOB_STARTED";
    public static final String COMPLETED = "SAMPLE_JOB_COMPLETED";
    public static final String FAILED = "SAMPLE_JOB_FAILED";
}
