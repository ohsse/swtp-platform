-- master 스키마 실도메인 1차 — 태그·공정·시설·설비 및 그 관계 6종.
-- 원천은 DA# ERD(docs/erd/스마트정수장.damx)이며, 이 파일은 그 익스포트를 옮긴 것이다.
-- ERD를 재익스포트해 대조할 수 있도록 원본 DDL의 형태(2단계 PK, 컬럼 순서, 제약 이름)를 그대로 유지한다.
--
-- 스키마명은 하드코딩하지 않는다 — spring.flyway.default-schema=master가 대상 스키마를 결정한다.
-- 식별자는 따옴표 없는 소문자로 적는다. PostgreSQL이 어차피 소문자로 폴딩하므로
-- ERD의 대문자 표기와 생성 결과는 동일하다.
--
-- ── PK 생성 방식: CREATE UNIQUE INDEX → ALTER TABLE ... PRIMARY KEY USING INDEX ──
-- PostgreSQL에서 정상 동작하는 구문이다. 단 이때 PG가 인덱스를 제약 이름으로 자동 개명하므로
-- 적용 후 <table>_u_idx 는 남지 않고 <table>_pkey 만 존재한다.
-- prcs_fclt_r_u_idx01 도 prcs_fclt_r_pkey 로 흡수되고, 업무 유니크인
-- prcs_fclt_r_u_idx02(prcs_id, fclt_id) 만 별도 인덱스로 남는다.
--
-- ── 감사 컬럼 ──
-- ERD 원본대로 mdf_dttm/mdf_id 를 NOT NULL 로 둔다 — 플랫폼 규약과 정확히 일치한다.
-- 따라서 이 4개 테이블(tag_m·prcs_m·fclt_m·eqp_m)에 매핑할 JPA 엔티티는 BaseEntity 를 그대로 상속하면 된다.
-- 수정하지 않는 prcs_fclt_r · eqp_tag_p 는 rgstr_* 만 두므로 BaseCreatedEntity 를 상속한다.
--
-- FK 제약은 걸지 않는다 — ERD 원본을 SSOT 로 유지하기 위해서다. 참조 정합성은 애플리케이션 책임.

-- ── 태그 ──────────────────────────────────────────────────────
CREATE TABLE tag_m (
    tag_sn      VARCHAR(30)  NOT NULL,
    tag_type_cd VARCHAR(3)   NOT NULL,
    use_yn      CHARACTER(1) NOT NULL DEFAULT 'Y',
    rgstr_dttm  TIMESTAMP    NOT NULL,
    rgstr_id    VARCHAR(50)  NOT NULL,
    mdf_dttm    TIMESTAMP    NOT NULL,
    mdf_id      VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  tag_m             IS '태그';
COMMENT ON COLUMN tag_m.tag_sn      IS '태그시리얼번호';
COMMENT ON COLUMN tag_m.tag_type_cd IS '태그유형코드';
COMMENT ON COLUMN tag_m.use_yn      IS '사용여부';
COMMENT ON COLUMN tag_m.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN tag_m.rgstr_id    IS '등록ID';
COMMENT ON COLUMN tag_m.mdf_dttm    IS '수정일시';
COMMENT ON COLUMN tag_m.mdf_id      IS '수정ID';

CREATE UNIQUE INDEX tag_m_u_idx ON tag_m (tag_sn);
ALTER TABLE tag_m ADD CONSTRAINT tag_m_pkey PRIMARY KEY USING INDEX tag_m_u_idx;

-- ── 공정 ──────────────────────────────────────────────────────
CREATE TABLE prcs_m (
    prcs_id      VARCHAR(36)  NOT NULL,
    prcs_nm      VARCHAR(50)  NOT NULL,
    prcs_type_cd VARCHAR(20)  NOT NULL,
    use_yn       CHARACTER(1) NOT NULL DEFAULT 'Y',
    sort_ord     INTEGER,
    rgstr_dttm   TIMESTAMP    NOT NULL,
    rgstr_id     VARCHAR(50)  NOT NULL,
    mdf_dttm     TIMESTAMP    NOT NULL,
    mdf_id       VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  prcs_m              IS '공정';
COMMENT ON COLUMN prcs_m.prcs_id      IS '공정ID';
COMMENT ON COLUMN prcs_m.prcs_nm      IS '공정명';
COMMENT ON COLUMN prcs_m.prcs_type_cd IS '공정유형코드';
COMMENT ON COLUMN prcs_m.use_yn       IS '사용여부';
COMMENT ON COLUMN prcs_m.sort_ord     IS '정렬순서';
COMMENT ON COLUMN prcs_m.rgstr_dttm   IS '등록일시';
COMMENT ON COLUMN prcs_m.rgstr_id     IS '등록ID';
COMMENT ON COLUMN prcs_m.mdf_dttm     IS '수정일시';
COMMENT ON COLUMN prcs_m.mdf_id       IS '수정ID';

CREATE UNIQUE INDEX prcs_m_u_idx ON prcs_m (prcs_id);
ALTER TABLE prcs_m ADD CONSTRAINT prcs_m_pkey PRIMARY KEY USING INDEX prcs_m_u_idx;

-- ── 시설 ──────────────────────────────────────────────────────
CREATE TABLE fclt_m (
    fclt_id      VARCHAR(36)  NOT NULL,
    fclt_nm      VARCHAR(50)  NOT NULL,
    fclt_type_cd VARCHAR(20)  NOT NULL,
    sort_ord     INTEGER,
    use_yn       CHARACTER(1) NOT NULL DEFAULT 'Y',
    rgstr_dttm   TIMESTAMP    NOT NULL,
    rgstr_id     VARCHAR(50)  NOT NULL,
    mdf_dttm     TIMESTAMP    NOT NULL,
    mdf_id       VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  fclt_m              IS '시설';
COMMENT ON COLUMN fclt_m.fclt_id      IS '시설ID';
COMMENT ON COLUMN fclt_m.fclt_nm      IS '시설명';
COMMENT ON COLUMN fclt_m.fclt_type_cd IS '시설유형코드';
COMMENT ON COLUMN fclt_m.sort_ord     IS '정렬순서';
COMMENT ON COLUMN fclt_m.use_yn       IS '사용여부';
COMMENT ON COLUMN fclt_m.rgstr_dttm   IS '등록일시';
COMMENT ON COLUMN fclt_m.rgstr_id     IS '등록ID';
COMMENT ON COLUMN fclt_m.mdf_dttm     IS '수정일시';
COMMENT ON COLUMN fclt_m.mdf_id       IS '수정ID';

CREATE UNIQUE INDEX fclt_m_u_idx ON fclt_m (fclt_id);
ALTER TABLE fclt_m ADD CONSTRAINT fclt_m_pkey PRIMARY KEY USING INDEX fclt_m_u_idx;

-- ── 공정시설관계 (M:N) ─────────────────────────────────────────
-- 등록만 하고 수정하지 않는 관계 테이블이므로 mdf_* 두 컬럼을 두지 않는다.
CREATE TABLE prcs_fclt_r (
    rel_id     VARCHAR(36) NOT NULL,
    prcs_id    VARCHAR(36) NOT NULL,
    fclt_id    VARCHAR(36) NOT NULL,
    rgstr_dttm TIMESTAMP   NOT NULL,
    rgstr_id   VARCHAR(50) NOT NULL
);

COMMENT ON TABLE  prcs_fclt_r            IS '공정시설관계';
COMMENT ON COLUMN prcs_fclt_r.rel_id     IS '관계ID';
COMMENT ON COLUMN prcs_fclt_r.prcs_id    IS '공정ID';
COMMENT ON COLUMN prcs_fclt_r.fclt_id    IS '시설ID';
COMMENT ON COLUMN prcs_fclt_r.rgstr_dttm IS '등록일시';
COMMENT ON COLUMN prcs_fclt_r.rgstr_id   IS '등록ID';

CREATE UNIQUE INDEX prcs_fclt_r_u_idx01 ON prcs_fclt_r (rel_id);
-- 같은 공정에 같은 시설이 두 번 매달리는 것을 막는 업무 유니크 — PK 로 흡수되지 않고 남는다
CREATE UNIQUE INDEX prcs_fclt_r_u_idx02 ON prcs_fclt_r (prcs_id, fclt_id);
ALTER TABLE prcs_fclt_r ADD CONSTRAINT prcs_fclt_r_pkey PRIMARY KEY USING INDEX prcs_fclt_r_u_idx01;

-- ── 설비 ──────────────────────────────────────────────────────
CREATE TABLE eqp_m (
    eqp_id      VARCHAR(36)  NOT NULL,
    fclt_id     VARCHAR(36)  NOT NULL,
    eqp_nm      VARCHAR(50)  NOT NULL,
    eqp_type_cd VARCHAR(20)  NOT NULL,
    use_yn      CHARACTER(1) NOT NULL DEFAULT 'Y',
    sort_ord    INTEGER,
    rgstr_dttm  TIMESTAMP    NOT NULL,
    rgstr_id    VARCHAR(50)  NOT NULL,
    mdf_dttm    TIMESTAMP    NOT NULL,
    mdf_id      VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  eqp_m             IS '설비';
COMMENT ON COLUMN eqp_m.eqp_id      IS '설비ID';
COMMENT ON COLUMN eqp_m.fclt_id     IS '시설ID';
COMMENT ON COLUMN eqp_m.eqp_nm      IS '설비명';
COMMENT ON COLUMN eqp_m.eqp_type_cd IS '설비유형코드';
COMMENT ON COLUMN eqp_m.use_yn      IS '사용여부';
COMMENT ON COLUMN eqp_m.sort_ord    IS '정렬순서';
COMMENT ON COLUMN eqp_m.rgstr_dttm  IS '등록일시';
COMMENT ON COLUMN eqp_m.rgstr_id    IS '등록ID';
COMMENT ON COLUMN eqp_m.mdf_dttm    IS '수정일시';
COMMENT ON COLUMN eqp_m.mdf_id      IS '수정ID';

CREATE UNIQUE INDEX eqp_m_u_idx ON eqp_m (eqp_id);
ALTER TABLE eqp_m ADD CONSTRAINT eqp_m_pkey PRIMARY KEY USING INDEX eqp_m_u_idx;

-- ── 설비태그 (명세) ────────────────────────────────────────────
-- 접미사 _P 는 명세 테이블이다. PK 가 tag_sn 단독인 것은 의도된 1:1 제약 —
-- 태그 하나는 설비 하나에만 귀속된다. 복합키로 바꾸면 이 제약이 사라진다.
CREATE TABLE eqp_tag_p (
    tag_sn     VARCHAR(30) NOT NULL,
    eqp_id     VARCHAR(36) NOT NULL,
    rgstr_dttm TIMESTAMP   NOT NULL,
    rgstr_id   VARCHAR(50) NOT NULL
);

COMMENT ON TABLE  eqp_tag_p            IS '설비태그';
COMMENT ON COLUMN eqp_tag_p.tag_sn     IS '태그시리얼번호';
COMMENT ON COLUMN eqp_tag_p.eqp_id     IS '설비ID';
COMMENT ON COLUMN eqp_tag_p.rgstr_dttm IS '등록일시';
COMMENT ON COLUMN eqp_tag_p.rgstr_id   IS '등록ID';

CREATE UNIQUE INDEX eqp_tag_p_u_idx ON eqp_tag_p (tag_sn);
ALTER TABLE eqp_tag_p ADD CONSTRAINT eqp_tag_p_pkey PRIMARY KEY USING INDEX eqp_tag_p_u_idx;
