-- [일회용 샘플] Phase 8 Job 파이프라인 관통 검증용 — 실제 Job 이력/집계 설계 착수 시 폐기한다.
-- 폐기 절차: ① com.mo.swtp.job.sample 패키지 삭제 ② 이 파일만 실제 설계로 교체 (V1 quartz / V2 batch는 영구 보존)
--            ③ docker compose down -v 로 볼륨 리셋 (개발 단계 — Flyway history 재작성 허용)
-- 스키마명 하드코딩 없음 — spring.flyway.default-schema=job 이 결정한다.

-- 실행 이력 — 아키텍처 8.6/8.7의 패턴 검증용 (상태 STOPPED, Trigger RETRY/RECOVERY는 이번 범위 외)
CREATE TABLE sample_job_execution (
    execution_id   UUID         PRIMARY KEY,
    job_name       VARCHAR(64)  NOT NULL,
    trigger_type   VARCHAR(16)  NOT NULL, -- SCHEDULED | MANUAL
    status         VARCHAR(16)  NOT NULL, -- STARTING | RUNNING | COMPLETED | FAILED
    requested_by   VARCHAR(64),           -- MANUAL 요청자 (인증 전 단계 — 요청 본문 값)
    started_at     TIMESTAMPTZ  NOT NULL,
    finished_at    TIMESTAMPTZ,
    failure_reason TEXT
);
CREATE INDEX idx_sample_job_execution_job ON sample_job_execution (job_name, started_at DESC);

-- Collector 자기 사본 — telemetry.raw 발행과 같은 트랜잭션으로 기록되는 집계 입력
-- (타 서비스 스키마 read/write 없이 job-service 안에서 집계가 완결되기 위한 원본)
CREATE TABLE sample_collected_measurement (
    measured_at TIMESTAMPTZ      NOT NULL,
    tag_id      VARCHAR(64)      NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (tag_id, measured_at)
);

-- Batch 집계 산출물 (분 → 10분 버킷)
CREATE TABLE sample_measurement_aggregate (
    window_start TIMESTAMPTZ      NOT NULL, -- date_bin('10 minutes') 버킷 시작
    tag_id       VARCHAR(64)      NOT NULL,
    avg_value    DOUBLE PRECISION NOT NULL,
    min_value    DOUBLE PRECISION NOT NULL,
    max_value    DOUBLE PRECISION NOT NULL,
    sample_count BIGINT           NOT NULL,
    PRIMARY KEY (tag_id, window_start)
);
