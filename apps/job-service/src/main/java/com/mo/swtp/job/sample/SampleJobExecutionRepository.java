package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mo.swtp.job.sample.SampleJobExecution.Status;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Job 실행 이력 저장소 — insert + 상태 update뿐이라 JPA 대신 JdbcClient를 쓴다 (Phase 8 결정).
 * SQL에 스키마명을 하드코딩하지 않는다 — 커넥션 기본 스키마(spring.datasource.hikari.schema=job)가 결정한다.
 */
@Repository
@RequiredArgsConstructor
public class SampleJobExecutionRepository {

    private static final RowMapper<SampleJobExecution> ROW_MAPPER = (rs, rowNum) -> {
        OffsetDateTime finishedAt = rs.getObject("finished_at", OffsetDateTime.class);
        return new SampleJobExecution(
                rs.getObject("execution_id", UUID.class),
                rs.getString("job_name"),
                SampleJobExecution.TriggerType.valueOf(rs.getString("trigger_type")),
                Status.valueOf(rs.getString("status")),
                rs.getString("requested_by"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                rs.getString("failure_reason"));
    };

    private final JdbcClient jdbcClient;

    public void insert(SampleJobExecution execution) {
        jdbcClient.sql("""
                        INSERT INTO sample_job_execution
                            (execution_id, job_name, trigger_type, status, requested_by, started_at)
                        VALUES (:executionId, :jobName, :triggerType, :status, :requestedBy, :startedAt)
                        """)
                .param("executionId", execution.executionId())
                .param("jobName", execution.jobName())
                .param("triggerType", execution.triggerType().name())
                .param("status", execution.status().name())
                .param("requestedBy", execution.requestedBy())
                .param("startedAt", OffsetDateTime.ofInstant(execution.startedAt(), ZoneOffset.UTC))
                .update();
    }

    public void markRunning(UUID executionId) {
        jdbcClient.sql("UPDATE sample_job_execution SET status = :status WHERE execution_id = :executionId")
                .param("status", Status.RUNNING.name())
                .param("executionId", executionId)
                .update();
    }

    /** 종결 상태(COMPLETED/FAILED)로 전이 — failureReason은 FAILED일 때만 값이 있다 */
    public void markFinished(UUID executionId, Status status, Instant finishedAt, String failureReason) {
        jdbcClient.sql("""
                        UPDATE sample_job_execution
                        SET status = :status, finished_at = :finishedAt, failure_reason = :failureReason
                        WHERE execution_id = :executionId
                        """)
                .param("status", status.name())
                .param("finishedAt", OffsetDateTime.ofInstant(finishedAt, ZoneOffset.UTC))
                .param("failureReason", failureReason)
                .param("executionId", executionId)
                .update();
    }

    public Optional<SampleJobExecution> findById(UUID executionId) {
        return jdbcClient.sql("SELECT * FROM sample_job_execution WHERE execution_id = :executionId")
                .param("executionId", executionId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** 최근 실행 이력 — jobName이 null이면 전체 */
    public List<SampleJobExecution> findRecent(String jobName, int limit) {
        if (jobName == null) {
            return jdbcClient.sql("SELECT * FROM sample_job_execution ORDER BY started_at DESC LIMIT :limit")
                    .param("limit", limit)
                    .query(ROW_MAPPER)
                    .list();
        }
        return jdbcClient.sql("""
                        SELECT * FROM sample_job_execution
                        WHERE job_name = :jobName
                        ORDER BY started_at DESC
                        LIMIT :limit
                        """)
                .param("jobName", jobName)
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }
}
