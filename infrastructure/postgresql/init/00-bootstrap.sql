-- 부트스트랩 전담: role/database 생성까지만 담당한다.
-- 이 파일은 두 경로에서 동일하게 실행된다:
--   1) docker: 볼륨 최초 생성 시 postgres 엔트리포인트가 1회만 자동 실행
--   2) 운영: DBA가 `psql -U postgres -f 00-bootstrap.sql`로 수동 1회 실행
-- 두 경로가 같은 파일을 타야 하므로 전 구문을 멱등(idempotent)하게 작성한다.
-- CREATE ROLE/CREATE DATABASE는 IF NOT EXISTS가 없어 psql \gexec 관용구로 멱등화한다.

-- 접속정보 SSOT: 전 환경 공통 (host만 환경별로 다르다)
--   database : swtp
--   role     : swtp_dba
--   password : swtp_local_dev
SELECT 'CREATE ROLE swtp_dba LOGIN PASSWORD ''swtp_local_dev'''
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'swtp_dba')\gexec

SELECT 'CREATE DATABASE swtp OWNER swtp_dba'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'swtp')\gexec

-- SUPERUSER는 부여하지 않는다. CREATE EXTENSION은 부트스트랩 실행자
-- (docker: 엔트리포인트의 postgres, 운영: DBA의 postgres 계정)가 01-schemas.sql에서 수행한다.
