-- operation 스키마 1차 — 운전모드 변경이력 1종.
-- EMS는 제어그룹 단위로, 자율운영은 공정 단위로 운전모드를 잡는다(아키텍처 11.3의 제어 분담).
-- 그 전환 이력을 한 테이블에 모아 "이 대상을 방금 누가 왜 건드렸나"를 한 번의 조회로 답한다.
--
-- 원천은 DA# ERD(docs/erd/스마트정수장.damx)의 DRVMD_CHG_H이며, 이 파일은 그 익스포트를 옮긴 것이다.
-- 다만 아래 「ERD 원문에서 실질 변경한 것」 4건은 ERD에 아직 없다 — 코드가 앞서 있다.
-- 결정 이력: apps/master-service/docs/02-운전모드-변경이력-DDL.md
--
-- 식별자는 따옴표 없는 소문자로 적는다. PostgreSQL이 어차피 소문자로 폴딩하므로
-- ERD의 대문자 표기와 생성 결과는 동일하다.
--
-- ── ⚠ 이 파일은 스키마명을 하드코딩한다 (V2·ems V1과 반대) ───────
-- V2__master_domain.sql은 "스키마명을 하드코딩하지 않는다"고 적었고 그것이 맞다.
-- 그 규칙이 성립하는 것은 대상이 default-schema일 때뿐이다.
-- master의 spring.flyway.default-schema는 master이므로, 접두어 없이 적으면
-- 이 테이블은 master.drvmd_chg_h 로 만들어진다.
-- spring.flyway.schemas: master,operation 은 Flyway의 *관리 대상 목록*일 뿐
-- 어느 스키마에 만들지를 정하지 않는다.
-- 이 함정은 조용하다 — 마이그레이션은 성공하고 테이블도 생기며, 다만 스키마가 틀린다.
-- Flyway 로그조차 잡아주지 못한다: 로그는 "Migrating schema master"라고만 말하는데
-- 그 "schema"는 히스토리 위치일 뿐이라 스키마를 틀려도 출력이 동일하다.
-- OperationMigrationIntegrationTest.테이블이_operation_스키마에_생성된다() 가 이 실패를 잡는
-- 유일한 장치다 — operation 에 있음과 master 에 없음을 함께 단언한다.
--
-- ── ERD 원문에서 형식만 정정한 것 ──────────────────────────────
-- 원문의 `DROP TABLE IF EXISTS ... CASCADE`는 뺐다. 버전 마이그레이션은 1회 적용에
-- 체크섬이 고정되므로 멱등성이 필요 없고, CASCADE는 재적용 사고 시 파괴 범위가 이 파일을 넘는다.
-- (멱등 작성은 infrastructure/postgresql/init/*.sql의 규약이지 Flyway의 규약이 아니다.)
-- 인덱스명은 원문의 <table>_pkey 대신 <table>_u_idx 로 적는다 — 아래 PK 생성 방식 참조.
--
-- ── ERD 원문에서 실질 변경한 것 (재익스포트 시 이 표를 먼저 볼 것) ──
-- ERD를 다시 뽑아 이 파일과 대조하면 아래 4건이 차이로 나타난다. 실수가 아니라 결정이다.
--   1. mdf_dttm / mdf_id 를 뺐다
--      step-14 규약 — "수정하지 않는 테이블에는 그 컬럼을 두지 않는다. 컬럼의 유무가 곧 그 선언이다."
--      append-only 이력 테이블에 수정 컬럼을 두면 감사 로그로서 자기모순이다.
--   2. ctrl_trgt_type_cd 를 넣었다
--      ctrl_trgt_id 는 다형 참조다(제어그룹 또는 공정). 지금은 iss_svc_cd 와 값이 1:1이지만
--      (EMS↔CTRL_GRP, AUTO↔PRCS) 축이 다르다 — iss_svc_cd 는 "어느 서비스에서 바꿨나",
--      이 값은 "무엇을 가리키나"다. 조회가 발행자로 조인 대상을 추론하기 시작하면
--      EMS 가 공정 모드를 다루게 되는 순간 조용히 깨지고, 그때 이미 쌓인 이력은 소급 해석이 불가능하다.
--   3. ctrl_trgt_nm 을 넣었다
--      제어그룹은 ems 스키마, 공정은 master 스키마에 있어 조인이 스키마 경계를 넘는다.
--      master가 통합 조회를 제공하면서 ems 스키마를 읽지 않으려면 명칭 스냅샷이 필요하다.
--      기준정보가 개명·소프트삭제돼도 "그 당시 이름"이 남는다는 감사 요건이기도 하다.
--   4. chg_rsn_cd / chg_rsn_rmrk 를 넣었다
--      "운전원이 AI 자동을 왜 껐나"는 사고 조사의 첫 질문이고 bf/af 코드로는 답이 나오지 않는다.
--   ※ DA# 재익스포트 전까지 ERD와 이 파일은 갈라져 있다. 되돌리는 쪽은 ERD다.
--
-- ── PK 생성 방식: CREATE UNIQUE INDEX → ALTER TABLE ... PRIMARY KEY USING INDEX ──
-- PostgreSQL에서 정상 동작하는 구문이다. 단 이때 PG가 인덱스를 제약 이름으로 자동 개명하므로
-- 적용 후 drvmd_chg_h_u_idx 는 남지 않고 drvmd_chg_h_pkey 만 존재한다.
--
-- ── 비유니크 인덱스 명명 ──────────────────────────────────────
-- ERD 계열(V2__master_domain.sql · ems V1)에는 비유니크 인덱스 선례가 없다 — 전부 PK용 _u_idx 뿐이다.
-- _u_idx(unique)와 대칭되도록 _i_idx01(index)로 두고 V2의 _u_idx01/02 번호 관례를 따른다.
--
-- ── 조회 인덱스가 ctrl_trgt_type_cd 로 시작하는 이유 ───────────
-- ctrl_trgt_id 는 애플리케이션이 부여하는 값이다 — master.prcs_m·ems.ctrl_grp_m 어디에도
-- 생성 전략(@GeneratedValue·UUID)이 없다. 따라서 공정과 제어그룹이 같은 ID 문자열을 가질 수 있고,
-- 판별자 없이 ctrl_trgt_id 만으로 조회하면 **두 대상의 이력이 섞여 나온다**.
-- ctrl_trgt_type_cd 를 둔 이유가 정확히 그것이므로 인덱스도 그것을 선두로 반영한다.
-- 판별자를 WHERE 에만 넣고 인덱스에서 빼면 필터로만 걸려 인덱스의 도움을 받지 못한다.
--
-- ── 감사 컬럼 ──────────────────────────────────────────────────
-- rgstr_* 만 둔다. 매핑할 JPA 엔티티는 BaseCreatedEntity 를 상속한다
-- (master.eqp_tag_p · ems.ctrl_eqp_p 와 동형).
-- 운전모드 전환은 언제나 사람이 한다 — 시스템이 스스로 모드를 낮추는 경로는 현재 설계에 없다.
-- 따라서 rgstr_id 는 항상 실제 운전원 계정이며 SYSTEM 이 들어갈 일이 없다.
-- iss_svc_cd 와 축이 다르다: rgstr_id 는 "누가", iss_svc_cd 는 "어느 서비스 화면에서".
--
-- ── write / read 분담 (이 테이블의 특수 계약) ──────────────────
-- 이 파일의 DDL 소유는 master-service이나, INSERT는 master가 하지 않는다.
-- 모드를 바꾼 서비스(ems · autonomous · 수동 전환 경로)가 **전환과 같은 트랜잭션에서 직접** 기록한다.
-- master가 대신 써 주면 전환은 성공했는데 이력만 실패하는 창이 생기고, iss_svc_cd 가
-- 호출자가 넘기는 위조 가능한 파라미터가 된다. 11.3의 "명령과 같은 트랜잭션에 동기 기록",
-- "Kafka로 이력 자체를 비동기 적재하지 않는다"가 같은 이유다.
-- 통합 조회(read)만 master가 맡는다. 쓰기는 분산, 읽기는 집중이다.
-- 컬럼이 필요해지면 이 파일에 추가한다 — 다른 서비스가 operation 스키마에 DDL을 걸면
-- 두 서비스가 같은 스키마에 마이그레이션을 걸어 Flyway history가 갈린다.
--
-- ── FK / CHECK ────────────────────────────────────────────────
-- FK 제약은 걸지 않는다 — ERD 원본을 SSOT 로 유지하기 위해서다(V2 헤더). 참조 정합성은 애플리케이션 책임.
-- ctrl_trgt_id 는 ems.ctrl_grp_m 또는 master.prcs_m 을 가리키는 다형 참조라 FK 자체가 불가능하다.
-- 코드값에 CHECK 도 걸지 않는다 — V2·V1 어디에도 선례가 없고 기존 *_type_cd 가 전부 애플리케이션 책임이다.

-- ── 운전모드변경 ──────────────────────────────────────────────
CREATE TABLE operation.drvmd_chg_h (
    hist_id           BIGSERIAL    NOT NULL,
    ctrl_trgt_type_cd VARCHAR(10)  NOT NULL,
    ctrl_trgt_id      VARCHAR(36)  NOT NULL,
    ctrl_trgt_nm      VARCHAR(50)  NOT NULL,
    bf_drvmd_cd       VARCHAR(10)  NOT NULL,
    af_drvmd_cd       VARCHAR(10)  NOT NULL,
    iss_svc_cd        VARCHAR(10)  NOT NULL,
    chg_rsn_cd        VARCHAR(10)  NOT NULL,
    chg_rsn_rmrk      VARCHAR(500),
    rgstr_dttm        TIMESTAMP    NOT NULL,
    rgstr_id          VARCHAR(50)  NOT NULL
);

COMMENT ON TABLE  operation.drvmd_chg_h                   IS '운전모드변경';
COMMENT ON COLUMN operation.drvmd_chg_h.hist_id           IS '이력ID';
COMMENT ON COLUMN operation.drvmd_chg_h.ctrl_trgt_type_cd IS '제어대상유형코드 — CTRL_GRP(제어그룹) 또는 PRCS(공정). ctrl_trgt_id의 해석 기준';
COMMENT ON COLUMN operation.drvmd_chg_h.ctrl_trgt_id      IS '제어대상ID — 유형코드에 따라 ems.ctrl_grp_m 또는 master.prcs_m 을 가리키는 다형 참조';
COMMENT ON COLUMN operation.drvmd_chg_h.ctrl_trgt_nm      IS '제어대상명 — 전환 시점의 명칭 스냅샷. 기준정보가 개명돼도 당시 이름을 보존한다';
COMMENT ON COLUMN operation.drvmd_chg_h.bf_drvmd_cd       IS '이전운전모드코드';
COMMENT ON COLUMN operation.drvmd_chg_h.af_drvmd_cd       IS '이후운전모드코드';
COMMENT ON COLUMN operation.drvmd_chg_h.iss_svc_cd        IS '발행서비스코드 — EMS/AUTO. 사람이 어느 서비스에서 모드를 바꿨는가';
COMMENT ON COLUMN operation.drvmd_chg_h.chg_rsn_cd        IS '변경사유코드 — OPRTR(운전원판단)/ANOMALY(이상감지)/ITLCK(인터록)/SCHED(스케줄) 등 11종';
COMMENT ON COLUMN operation.drvmd_chg_h.chg_rsn_rmrk      IS '변경사유비고 — 코드로 표현되지 않는 서술';
COMMENT ON COLUMN operation.drvmd_chg_h.rgstr_dttm        IS '등록일시';
COMMENT ON COLUMN operation.drvmd_chg_h.rgstr_id          IS '등록ID — 모드를 바꾼 운전원 계정. 모드 전환은 언제나 사람이 하므로 항상 실제 계정이다';

CREATE UNIQUE INDEX drvmd_chg_h_u_idx ON operation.drvmd_chg_h (hist_id);
ALTER TABLE operation.drvmd_chg_h
    ADD CONSTRAINT drvmd_chg_h_pkey PRIMARY KEY USING INDEX drvmd_chg_h_u_idx;

-- 지배적 조회는 "사고 시각에 이 대상이 무슨 모드였나" — 대상 고정 + 시간 역순이다.
CREATE INDEX drvmd_chg_h_i_idx01
    ON operation.drvmd_chg_h (ctrl_trgt_type_cd, ctrl_trgt_id, rgstr_dttm DESC);
