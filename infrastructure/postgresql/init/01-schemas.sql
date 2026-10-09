-- 스키마별 데이터 소유권 원칙: 이 스크립트는 확장(extension)과 스키마 생성까지만 담당한다.
-- 테이블 DDL은 각 서비스의 Flyway 마이그레이션이 소유한다 (Phase 6~).
-- Flyway는 create-schemas: false로 배선되어 있다 — 스키마 생성 주체는 이 파일 단독이다.
--
-- 00-bootstrap.sql과 마찬가지로 docker 자동 실행 / 운영 DBA 수동 실행 두 경로에서 동일하게
-- 동작해야 하므로, \connect로 대상 DB를 파일이 직접 확정하고 전 구문을 멱등하게 작성한다.
\connect swtp

-- ── 확장(extension) ──────────────────────────────────────────
-- postgis 등 재배치 가능한(relocatable) 확장은 ext 스키마에 격리해 public을 깨끗하게 유지한다.
CREATE SCHEMA IF NOT EXISTS ext AUTHORIZATION swtp_dba;
GRANT USAGE ON SCHEMA ext TO PUBLIC;

-- timescaledb는 relocatable이 아니라 SCHEMA 지정이 불가하다 — public 고정.
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis SCHEMA ext;
-- 추가 예정: CREATE EXTENSION IF NOT EXISTS tablefunc SCHEMA ext;

-- DB 기본 search_path에 ext를 포함해, 각 서비스가 커넥션 초기화 시 지정하는
-- "자기 스키마, public, ext" 경로와 일관되게 유지한다.
ALTER DATABASE swtp SET search_path = "$user", public, ext;

-- ── 업무 스키마 (테이블 DDL은 각 서비스 Flyway 소유) ───────────
CREATE SCHEMA IF NOT EXISTS master    AUTHORIZATION swtp_dba; -- master-service 소유
-- operation: 제어 명령 이력 + 운전모드. DDL 소유는 master-service이나,
-- write는 제어 권한을 가진 서비스(autonomous/ems/manual)가 공용으로 한다.
-- 문서 11.3 데이터 소유권 원칙의 명시적 예외 — job-service의 batch/quartz 겸유 소유 선례와 동일 계열.
CREATE SCHEMA IF NOT EXISTS operation AUTHORIZATION swtp_dba;
CREATE SCHEMA IF NOT EXISTS telemetry AUTHORIZATION swtp_dba; -- telemetry-service 소유 (hypertable)
CREATE SCHEMA IF NOT EXISTS ems       AUTHORIZATION swtp_dba; -- ems-service 소유
CREATE SCHEMA IF NOT EXISTS pms       AUTHORIZATION swtp_dba; -- pms-service 소유
CREATE SCHEMA IF NOT EXISTS auth      AUTHORIZATION swtp_dba; -- auth-service 소유
CREATE SCHEMA IF NOT EXISTS job       AUTHORIZATION swtp_dba; -- job-service 소유
CREATE SCHEMA IF NOT EXISTS batch     AUTHORIZATION swtp_dba; -- Spring Batch 메타 테이블 (job-service 소유)
CREATE SCHEMA IF NOT EXISTS quartz    AUTHORIZATION swtp_dba; -- Quartz 스케줄러 테이블 (job-service 소유)
