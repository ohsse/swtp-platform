-- [일회용 샘플] Phase 7 시계열 파이프라인 관통 검증용 — 실제 telemetry 설계(tag_value 등) 착수 시 폐기한다.
-- 폐기 절차: ① com.mo.swtp.telemetry.sample 패키지 삭제 ② 이 파일을 실제 설계의 V1으로 교체
--            ③ docker compose down -v 로 볼륨 리셋 (개발 단계 — Flyway history 재작성 허용)
-- 스키마명 하드코딩 없음 — spring.flyway.default-schema=telemetry 가 결정한다.
CREATE TABLE sample_measurement (
    measured_at TIMESTAMPTZ      NOT NULL, -- 측정 시각 (봉투의 occurredAt=발행 시각과 구분)
    tag_id      VARCHAR(64)      NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    -- hypertable 제약: PK는 파티션 컬럼(measured_at)을 포함해야 한다
    PRIMARY KEY (tag_id, measured_at)
);

-- 아키텍처 문서 12.3 초기 운영 기준: chunk interval 7일
-- extension 함수는 public. 정규화 필수 — Flyway 마이그레이션 커넥션의 search_path는 telemetry뿐이라
-- 인프라 init이 public에 설치한 timescaledb 함수가 정규화 없이는 보이지 않는다 (hypertable 마이그레이션 규약)
SELECT public.create_hypertable('sample_measurement', public.by_range('measured_at', INTERVAL '7 days'));
