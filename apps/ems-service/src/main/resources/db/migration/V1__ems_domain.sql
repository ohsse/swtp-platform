-- ems 실도메인 1차 — 제어그룹 계열 5종.
-- EMS가 송수펌프 설비를 제어그룹 단위로 묶고(ctrl_grp_m ← ctrl_eqp_p · ctrl_grp_tag_p),
-- 그 펌프 제어에 따라 물이 어느 분기점으로 흐르는지를 수계통지점으로 표현한다(wnp_m ← ctrl_grp_wnp_p).
-- 제어 분담은 아키텍처 11.3이 축으로 갈라 뒀다 — autonomous는 송수를 제외한 공정, ems는 송수펌프·밸브.
--
-- 원천은 DA# ERD(docs/erd/스마트정수장.damx)이며, 이 파일은 그 익스포트를 옮긴 것이다.
-- ERD를 재익스포트해 대조할 수 있도록 원본 DDL의 형태(2단계 PK, 컬럼 순서, 단독키 PK)를 그대로 유지한다.
-- 결정 이력: apps/ems-service/docs/01-제어그룹-도메인-DDL.md
--
-- 스키마명은 하드코딩하지 않는다 — spring.flyway.default-schema=ems가 대상 스키마를 결정한다.
-- 식별자는 따옴표 없는 소문자로 적는다. PostgreSQL이 어차피 소문자로 폴딩하므로
-- ERD의 대문자 표기와 생성 결과는 동일하다.
--
-- ── ERD 원문에서 정정한 것 ────────────────────────────────────
-- 원문의 `DROP TABLE IF EXISTS ... CASCADE`는 전부 뺐다. 버전 마이그레이션은 1회 적용에
-- 체크섬이 고정되므로 멱등성이 필요 없고, CASCADE는 재적용 사고 시 파괴 범위가 이 파일을 넘는다.
-- (멱등 작성은 infrastructure/postgresql/init/*.sql의 규약이지 Flyway의 규약이 아니다.
--  같은 이유로 V1__quartz_schema.sql도 원본의 DROP 블록을 제거했다.)
-- 인덱스명은 원문의 <table>_pkey 대신 <table>_u_idx 로 적는다 — 아래 PK 생성 방식 참조.
-- ctrl_grp_tag_p는 원문에서 감사 컬럼 사이에 sort_ord가 끼어 있어 업무컬럼 → rgstr_* → mdf_* 로 정렬했다.
--
-- ── PK 생성 방식: CREATE UNIQUE INDEX → ALTER TABLE ... PRIMARY KEY USING INDEX ──
-- PostgreSQL에서 정상 동작하는 구문이다. 단 이때 PG가 인덱스를 제약 이름으로 자동 개명하므로
-- 적용 후 <table>_u_idx 는 남지 않고 <table>_pkey 만 존재한다.
--
-- ── 단독키 PK는 의도된 제약이다 ────────────────────────────────
-- ctrl_eqp_p(eqp_id) · ctrl_grp_wnp_p(wnp_id) · ctrl_grp_tag_p(tag_sn) 셋 다 복합키가 아니다.
-- "설비/지점/태그 하나는 제어그룹 하나에만 속한다"는 뜻이며, master.eqp_tag_p가 같은 형태다.
-- 복합키로 바꾸면 이 제약이 사라진다. 완화가 필요해지면 ERD를 먼저 고치고 재익스포트한다.
--
-- ── 감사 컬럼 ──────────────────────────────────────────────────
-- step-14 규약: 컬럼이 있으면 NOT NULL이고, 수정하지 않는 테이블에는 그 컬럼을 두지 않는다.
-- 따라서 mdf_* 를 가진 4개(ctrl_grp_m · wnp_m · ctrl_grp_wnp_p · ctrl_grp_tag_p)는 BaseEntity 를,
-- rgstr_* 만 두는 ctrl_eqp_p 는 BaseCreatedEntity 를 상속한다.
--
-- ── 교차 스키마 참조 ──────────────────────────────────────────
-- FK 제약은 걸지 않는다 — ERD 원본을 SSOT 로 유지하기 위해서다. 참조 정합성은 애플리케이션 책임.
-- 특히 ctrl_eqp_p.eqp_id 와 ctrl_grp_tag_p.tag_sn 은 master 스키마가 소유한 키의 로컬 복제본이다.
-- ems 는 master 스키마에 직접 붙지 않으므로(아키텍처 11.3) 유효성은 master HTTP 조회로 확인한다.
-- master 는 물리 삭제 API가 없고 use_yn='N' 소프트 삭제만 하므로 고아 행은 조회 시점 필터로 흡수된다.

-- ── 제어그룹 ──────────────────────────────────────────────────
CREATE TABLE ctrl_grp_m (
    ctrl_grp_id VARCHAR(36)  NOT NULL,
    ctrl_grp_nm VARCHAR(50)  NOT NULL,
    use_yn      CHARACTER(1) NOT NULL DEFAULT 'Y',
    sort_ord    INTEGER,
    rgstr_dttm  TIMESTAMP    NOT NULL,
    rgstr_id    VARCHAR(50)  NOT NULL,
    mdf_dttm    TIMESTAMP    NOT NULL,
    mdf_id      VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  ctrl_grp_m             IS '제어그룹';
COMMENT ON COLUMN ctrl_grp_m.ctrl_grp_id IS '제어그룹ID';
COMMENT ON COLUMN ctrl_grp_m.ctrl_grp_nm IS '제어그룹명';
COMMENT ON COLUMN ctrl_grp_m.use_yn      IS '사용여부';
COMMENT ON COLUMN ctrl_grp_m.sort_ord    IS '정렬순서';
COMMENT ON COLUMN ctrl_grp_m.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN ctrl_grp_m.rgstr_id    IS '등록ID';
COMMENT ON COLUMN ctrl_grp_m.mdf_dttm    IS '수정일시';
COMMENT ON COLUMN ctrl_grp_m.mdf_id      IS '수정ID';

CREATE UNIQUE INDEX ctrl_grp_m_u_idx ON ctrl_grp_m (ctrl_grp_id);
ALTER TABLE ctrl_grp_m ADD CONSTRAINT ctrl_grp_m_pkey PRIMARY KEY USING INDEX ctrl_grp_m_u_idx;

-- ── 수계통지점 ────────────────────────────────────────────────
-- 펌프 제어의 결과로 물이 흐르는 분기점이다. 공정(master.prcs_m)·시설(master.fclt_m)과 다른 축이며,
-- EMS 제어 토폴로지의 일부이므로 ems 가 소유한다.
CREATE TABLE wnp_m (
    wnp_id     VARCHAR(36)  NOT NULL,
    wnp_nm     VARCHAR(50)  NOT NULL,
    use_yn     CHARACTER(1) NOT NULL DEFAULT 'Y',
    sort_ord   INTEGER,
    rgstr_dttm TIMESTAMP    NOT NULL,
    rgstr_id   VARCHAR(50)  NOT NULL,
    mdf_dttm   TIMESTAMP    NOT NULL,
    mdf_id     VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  wnp_m            IS '수계통지점';
COMMENT ON COLUMN wnp_m.wnp_id     IS '수계통지점ID';
COMMENT ON COLUMN wnp_m.wnp_nm     IS '수계통지점명';
COMMENT ON COLUMN wnp_m.use_yn     IS '사용여부';
COMMENT ON COLUMN wnp_m.sort_ord   IS '정렬순서';
COMMENT ON COLUMN wnp_m.rgstr_dttm IS '등록일시';
COMMENT ON COLUMN wnp_m.rgstr_id   IS '등록ID';
COMMENT ON COLUMN wnp_m.mdf_dttm   IS '수정일시';
COMMENT ON COLUMN wnp_m.mdf_id     IS '수정ID';

CREATE UNIQUE INDEX wnp_m_u_idx ON wnp_m (wnp_id);
ALTER TABLE wnp_m ADD CONSTRAINT wnp_m_pkey PRIMARY KEY USING INDEX wnp_m_u_idx;

-- ── 제어설비 (명세) ───────────────────────────────────────────
-- eqp_id 는 master.eqp_m 이 소유한 키의 복제본이다. mdf_* 가 없다 —
-- 편성을 UPDATE 하지 않고 삭제 후 재등록한다는 선언이다(step-14 규약).
-- sort_ord 재정렬을 UPDATE 로 하려면 mdf_* 를 추가해 BaseEntity 로 올려야 한다.
CREATE TABLE ctrl_eqp_p (
    ctrl_grp_id VARCHAR(36) NOT NULL,
    eqp_id      VARCHAR(36) NOT NULL,
    sort_ord    INTEGER,
    rgstr_dttm  TIMESTAMP   NOT NULL,
    rgstr_id    VARCHAR(50) NOT NULL
);

COMMENT ON TABLE  ctrl_eqp_p             IS '제어설비';
COMMENT ON COLUMN ctrl_eqp_p.ctrl_grp_id IS '제어그룹ID';
COMMENT ON COLUMN ctrl_eqp_p.eqp_id      IS '설비ID';
COMMENT ON COLUMN ctrl_eqp_p.sort_ord    IS '정렬순서';
COMMENT ON COLUMN ctrl_eqp_p.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN ctrl_eqp_p.rgstr_id    IS '등록ID';

CREATE UNIQUE INDEX ctrl_eqp_p_u_idx ON ctrl_eqp_p (eqp_id);
ALTER TABLE ctrl_eqp_p ADD CONSTRAINT ctrl_eqp_p_pkey PRIMARY KEY USING INDEX ctrl_eqp_p_u_idx;

-- ── 제어그룹수계통지점 (명세) ─────────────────────────────────
CREATE TABLE ctrl_grp_wnp_p (
    ctrl_grp_id VARCHAR(36) NOT NULL,
    wnp_id      VARCHAR(36) NOT NULL,
    sort_ord    INTEGER,
    rgstr_dttm  TIMESTAMP   NOT NULL,
    rgstr_id    VARCHAR(50) NOT NULL,
    mdf_dttm    TIMESTAMP   NOT NULL,
    mdf_id      VARCHAR(50) NOT NULL
);

COMMENT ON TABLE  ctrl_grp_wnp_p             IS '제어그룹수계통지점';
COMMENT ON COLUMN ctrl_grp_wnp_p.ctrl_grp_id IS '제어그룹ID';
COMMENT ON COLUMN ctrl_grp_wnp_p.wnp_id      IS '수계통지점ID';
COMMENT ON COLUMN ctrl_grp_wnp_p.sort_ord    IS '정렬순서';
COMMENT ON COLUMN ctrl_grp_wnp_p.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN ctrl_grp_wnp_p.rgstr_id    IS '등록ID';
COMMENT ON COLUMN ctrl_grp_wnp_p.mdf_dttm    IS '수정일시';
COMMENT ON COLUMN ctrl_grp_wnp_p.mdf_id      IS '수정ID';

CREATE UNIQUE INDEX ctrl_grp_wnp_p_u_idx ON ctrl_grp_wnp_p (wnp_id);
ALTER TABLE ctrl_grp_wnp_p ADD CONSTRAINT ctrl_grp_wnp_p_pkey PRIMARY KEY USING INDEX ctrl_grp_wnp_p_u_idx;

-- ── 제어그룹태그 (명세) ───────────────────────────────────────
-- tag_sn 은 master.tag_m 이 소유한 키의 복제본이다.
CREATE TABLE ctrl_grp_tag_p (
    tag_sn      VARCHAR(30) NOT NULL,
    ctrl_grp_id VARCHAR(36) NOT NULL,
    sort_ord    INTEGER,
    rgstr_dttm  TIMESTAMP   NOT NULL,
    rgstr_id    VARCHAR(50) NOT NULL,
    mdf_dttm    TIMESTAMP   NOT NULL,
    mdf_id      VARCHAR(50) NOT NULL
);

COMMENT ON TABLE  ctrl_grp_tag_p             IS '제어그룹태그';
COMMENT ON COLUMN ctrl_grp_tag_p.tag_sn      IS '태그시리얼번호';
COMMENT ON COLUMN ctrl_grp_tag_p.ctrl_grp_id IS '제어그룹ID';
COMMENT ON COLUMN ctrl_grp_tag_p.sort_ord    IS '정렬순서';
COMMENT ON COLUMN ctrl_grp_tag_p.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN ctrl_grp_tag_p.rgstr_id    IS '등록ID';
COMMENT ON COLUMN ctrl_grp_tag_p.mdf_dttm    IS '수정일시';
COMMENT ON COLUMN ctrl_grp_tag_p.mdf_id      IS '수정ID';

CREATE UNIQUE INDEX ctrl_grp_tag_p_u_idx ON ctrl_grp_tag_p (tag_sn);
ALTER TABLE ctrl_grp_tag_p ADD CONSTRAINT ctrl_grp_tag_p_pkey PRIMARY KEY USING INDEX ctrl_grp_tag_p_u_idx;
