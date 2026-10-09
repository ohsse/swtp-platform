package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.Instant;

/**
 * Collector 수집 측정값 — telemetry.raw payload 계약이자 집계 입력의 자기 사본 행.
 *
 * <p><b>필드명은 telemetry-service의 payload 계약(tagId/value/measuredAt)과 동일해야 한다</b> —
 * 서비스 간 계약은 JSON 구조로만 유지되므로(Consumer 규약) 이 이름이 곧 E2E 성립 조건이다.
 */
public record SampleCollectedMeasurement(String tagId, double value, Instant measuredAt) {
}
