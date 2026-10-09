package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

/**
 * 측정값 수집 도메인 이벤트 — 자기 사본 insert와 같은 트랜잭션에서 발행되고,
 * 릴레이가 커밋 후 telemetry.raw 토픽으로 중계한다 ("발행자는 자기 사본을 가진다").
 */
public record SampleMeasurementCollectedEvent(SampleCollectedMeasurement measurement) {
}
