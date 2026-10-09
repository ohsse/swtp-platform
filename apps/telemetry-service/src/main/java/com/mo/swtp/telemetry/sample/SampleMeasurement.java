package com.mo.swtp.telemetry.sample;

import java.time.Instant;

/**
 * [일회용 샘플] telemetry.raw payload 계약이자 조회 응답 — 실제 telemetry 설계 착수 시 폐기한다.
 *
 * @param tagId      계측 태그 ID
 * @param value      측정값
 * @param measuredAt 측정 시각 (UTC) — 봉투의 occurredAt(발행 시각)과 구분한다
 */
public record SampleMeasurement(String tagId, double value, Instant measuredAt) {
}
