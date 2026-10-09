-- auth 스키마 테이블 DDL — auth-service 단독 소유 (스키마 생성은 01-schemas.sql 담당)
-- 스키마명을 하드코딩하지 않는다: spring.flyway.default-schema=auth가 대상 스키마를 결정한다.
--
-- 감사 컬럼 규약
--   등록만 하는 테이블  : rgstr_dttm, rgstr_id                       (BaseCreatedEntity)
--   수정도 하는 테이블  : + mdf_dttm, mdf_id (모두 NOT NULL)          (BaseEntity)
--   수정 컬럼은 등록 시점에 등록값과 동일하게 함께 채워진다 — "컬럼이 있으면 값이 반드시 있다".
--   수정하지 않는 테이블에는 mdf_* 두 컬럼을 아예 두지 않는다: 컬럼의 유무가 곧 그 선언이다.
--   "한 번도 수정되지 않음"은 mdf_dttm = rgstr_dttm 으로 판정한다.
--   값은 애플리케이션(JPA Auditing)이 채우므로 DB DEFAULT를 걸지 않는다.

-- ── 사용자 ────────────────────────────────────────────────────
CREATE TABLE users (
    user_id       VARCHAR(50)  PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    rgstr_dttm    TIMESTAMP    NOT NULL,
    rgstr_id      VARCHAR(50)  NOT NULL,
    mdf_dttm      TIMESTAMP    NOT NULL,
    mdf_id        VARCHAR(50)  NOT NULL
);
CREATE UNIQUE INDEX ux_users_username ON users (username);

COMMENT ON TABLE  users               IS '플랫폼 사용자';
COMMENT ON COLUMN users.user_id       IS '사용자 식별자 — 불변. 토큰 sub 및 감사 컬럼(rgstr_id/mdf_id)의 값';
COMMENT ON COLUMN users.username      IS '로그인 ID — 변경 가능하므로 식별자로 쓰지 않는다';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 해시';

-- ── 역할 ─────────────────────────────────────────────────────
CREATE TABLE roles (
    role_id     VARCHAR(50)  PRIMARY KEY,
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    rgstr_dttm  TIMESTAMP    NOT NULL,
    rgstr_id    VARCHAR(50)  NOT NULL,
    mdf_dttm    TIMESTAMP    NOT NULL,
    mdf_id      VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  roles         IS '역할';
COMMENT ON COLUMN roles.role_id IS 'JWT roles 클레임에 그대로 실리는 역할 코드 (예: ADMIN) — ROLE_ 접두사는 붙이지 않는다';

-- 사용자-역할 매핑: 부여/회수는 행 추가·삭제로 표현하고 행 자체는 수정하지 않는다 → 등록 컬럼만
CREATE TABLE user_roles (
    user_role_id BIGSERIAL   PRIMARY KEY,
    user_id      VARCHAR(50) NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    role_id      VARCHAR(50) NOT NULL REFERENCES roles (role_id),
    rgstr_dttm   TIMESTAMP   NOT NULL,
    rgstr_id     VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX ux_user_roles ON user_roles (user_id, role_id);

COMMENT ON TABLE user_roles IS '사용자-역할 매핑 (등록 전용 — 회수는 행 삭제)';

-- ── 권한 ─────────────────────────────────────────────────────
-- 아키텍처 4.1의 "Permission 관리" 대상. Phase 10 범위는 인증(토큰 발급·검증)까지이며,
-- 권한 기반 인가 슬라이스에서 엔티티/API를 붙인다. 스키마를 먼저 확정해 두어
-- 그때 테이블 추가 마이그레이션이 아니라 사용만 하면 되게 한다.
CREATE TABLE permissions (
    permission_id VARCHAR(50)  PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    rgstr_dttm    TIMESTAMP    NOT NULL,
    rgstr_id      VARCHAR(50)  NOT NULL,
    mdf_dttm      TIMESTAMP    NOT NULL,
    mdf_id        VARCHAR(50)  NOT NULL
);

CREATE TABLE role_permissions (
    role_permission_id BIGSERIAL   PRIMARY KEY,
    role_id            VARCHAR(50) NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    permission_id      VARCHAR(50) NOT NULL REFERENCES permissions (permission_id),
    rgstr_dttm         TIMESTAMP   NOT NULL,
    rgstr_id           VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX ux_role_permissions ON role_permissions (role_id, permission_id);

COMMENT ON TABLE permissions      IS '권한 (인가 슬라이스에서 사용)';
COMMENT ON TABLE role_permissions IS '역할-권한 매핑 (등록 전용)';

-- ── 리프레시 토큰 ─────────────────────────────────────────────
-- Redis를 쓰지 않는다(원칙 15) — 폐기 가능한 공유 상태이므로 PostgreSQL에 둔다(원칙 16).
CREATE TABLE refresh_tokens (
    refresh_token_id BIGSERIAL   PRIMARY KEY,
    token_hash       CHAR(64)    NOT NULL,
    user_id          VARCHAR(50) NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    expires_dttm     TIMESTAMP   NOT NULL,
    revoked_dttm     TIMESTAMP,
    rgstr_dttm       TIMESTAMP   NOT NULL,
    rgstr_id         VARCHAR(50) NOT NULL,
    mdf_dttm         TIMESTAMP   NOT NULL,
    mdf_id           VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

COMMENT ON TABLE  refresh_tokens             IS '리프레시 토큰 (회전 발급 — 사용 즉시 폐기하고 새로 발급)';
COMMENT ON COLUMN refresh_tokens.token_hash  IS 'SHA-256 hex. 원문은 저장하지 않는다 — DB가 유출돼도 토큰을 재사용할 수 없게';
COMMENT ON COLUMN refresh_tokens.revoked_dttm IS '폐기 시각. NULL이면 유효';

-- ── JWT 서명키 ────────────────────────────────────────────────
CREATE TABLE jwt_signing_keys (
    kid         VARCHAR(50) PRIMARY KEY,
    public_key  TEXT        NOT NULL,
    private_key TEXT        NOT NULL,
    algorithm   VARCHAR(20) NOT NULL,
    active      BOOLEAN     NOT NULL,
    rgstr_dttm  TIMESTAMP   NOT NULL,
    rgstr_id    VARCHAR(50) NOT NULL,
    mdf_dttm    TIMESTAMP   NOT NULL,
    mdf_id      VARCHAR(50) NOT NULL
);
-- 부분 유니크 인덱스로 "활성 키는 최대 1개"를 DB가 보장한다.
-- 최초 기동 시 인스턴스 여러 개가 동시에 키를 만들어도 한쪽만 성공하고 나머지는 그 키를 읽게 된다.
CREATE UNIQUE INDEX ux_jwt_signing_keys_active ON jwt_signing_keys (active) WHERE active;

COMMENT ON TABLE  jwt_signing_keys             IS 'JWT RS256 서명 키쌍';
COMMENT ON COLUMN jwt_signing_keys.kid         IS 'JWK Key ID — 토큰 헤더에 실려 검증 측이 공개키를 고른다';
COMMENT ON COLUMN jwt_signing_keys.public_key  IS 'Base64(X.509 SubjectPublicKeyInfo) — JWKS로 공개';
COMMENT ON COLUMN jwt_signing_keys.private_key IS 'Base64(PKCS#8). 평문 보관 — 운영 배포 시 KMS/시크릿 매니저로 이전 필요';
