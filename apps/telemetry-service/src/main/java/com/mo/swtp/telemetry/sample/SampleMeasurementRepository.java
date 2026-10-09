package com.mo.swtp.telemetry.sample;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * [일회용 샘플] 시계열 적재/조회 — 실제 telemetry 설계 착수 시 폐기한다.
 *
 * <p>append-only 시계열이라 JPA 대신 JdbcClient를 쓴다 (Phase 7 결정).
 * SQL에 스키마명을 하드코딩하지 않는다 — 커넥션 기본 스키마(spring.datasource.hikari.schema)가 결정한다.
 */
@Repository
@RequiredArgsConstructor
public class SampleMeasurementRepository {

    private static final RowMapper<SampleMeasurement> ROW_MAPPER = (rs, rowNum) -> new SampleMeasurement(
            rs.getString("tag_id"),
            rs.getDouble("value"),
            rs.getObject("measured_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbcClient;

    /** 적재 — 동일 (tag_id, measured_at) 재소비는 무시한다 (Kafka 재전달 멱등) */
    public void insert(SampleMeasurement measurement) {
        jdbcClient.sql("""
                        INSERT INTO sample_measurement (measured_at, tag_id, value)
                        VALUES (:measuredAt, :tagId, :value)
                        ON CONFLICT (tag_id, measured_at) DO NOTHING
                        """)
                .param("measuredAt", OffsetDateTime.ofInstant(measurement.measuredAt(), ZoneOffset.UTC))
                .param("tagId", measurement.tagId())
                .param("value", measurement.value())
                .update();
    }

    /** 기간 조회 — 측정 시각 오름차순 */
    public List<SampleMeasurement> findByTagIdBetween(String tagId, Instant from, Instant to) {
        return jdbcClient.sql("""
                        SELECT measured_at, tag_id, value
                        FROM sample_measurement
                        WHERE tag_id = :tagId AND measured_at BETWEEN :from AND :to
                        ORDER BY measured_at
                        """)
                .param("tagId", tagId)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query(ROW_MAPPER)
                .list();
    }

    /** 현재값(최신 측정) 조회 */
    public Optional<SampleMeasurement> findLatest(String tagId) {
        return jdbcClient.sql("""
                        SELECT measured_at, tag_id, value
                        FROM sample_measurement
                        WHERE tag_id = :tagId
                        ORDER BY measured_at DESC
                        LIMIT 1
                        """)
                .param("tagId", tagId)
                .query(ROW_MAPPER)
                .optional();
    }
}
