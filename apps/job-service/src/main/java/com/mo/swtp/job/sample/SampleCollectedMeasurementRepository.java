package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Collector 자기 사본 적재 + 10분 버킷 집계.
 * SQL에 스키마명을 하드코딩하지 않는다 — 커넥션 기본 스키마(spring.datasource.hikari.schema=job)가 결정한다.
 */
@Repository
@RequiredArgsConstructor
public class SampleCollectedMeasurementRepository {

    private final JdbcClient jdbcClient;

    /** 수집 기록 — 동일 (tag_id, measured_at) 재수집은 무시한다 (재실행 멱등) */
    public void insert(SampleCollectedMeasurement measurement) {
        jdbcClient.sql("""
                        INSERT INTO sample_collected_measurement (measured_at, tag_id, value)
                        VALUES (:measuredAt, :tagId, :value)
                        ON CONFLICT (tag_id, measured_at) DO NOTHING
                        """)
                .param("measuredAt", OffsetDateTime.ofInstant(measurement.measuredAt(), ZoneOffset.UTC))
                .param("tagId", measurement.tagId())
                .param("value", measurement.value())
                .update();
    }

    /**
     * 분 단위 원본 → 10분 버킷(avg/min/max/count) 전량 재집계 UPSERT.
     * 전량 재집계라 몇 번을 다시 돌려도 결과가 같다 (멱등) — 증분 윈도우는 실설계 항목.
     *
     * @return 적재(신규+갱신)된 버킷 수
     */
    public int aggregate() {
        return jdbcClient.sql("""
                        INSERT INTO sample_measurement_aggregate
                            (window_start, tag_id, avg_value, min_value, max_value, sample_count)
                        SELECT date_bin('10 minutes', measured_at, TIMESTAMPTZ '2000-01-01') AS window_start,
                               tag_id, avg(value), min(value), max(value), count(*)
                        FROM sample_collected_measurement
                        GROUP BY window_start, tag_id
                        ON CONFLICT (tag_id, window_start) DO UPDATE
                        SET avg_value = EXCLUDED.avg_value,
                            min_value = EXCLUDED.min_value,
                            max_value = EXCLUDED.max_value,
                            sample_count = EXCLUDED.sample_count
                        """)
                .update();
    }
}
