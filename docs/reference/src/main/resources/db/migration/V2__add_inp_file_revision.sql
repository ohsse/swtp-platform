-- INP 파일 리비전(이력) 관리 도입 [2단계 마이그레이션 중 1단계]
-- 마스터(inp_file_m)는 '현재 적용 리비전 번호(curr_rev_no)'만 가리키는 포인터를 갖고,
-- 실제 저장 파일명/크기/해시/작업구분 등 리비전별 정보는 이력 테이블(inp_file_rev_h)에 append-only 로 쌓인다.
--
-- 안전한 롤아웃을 위해 컬럼 제거를 2단계로 나눈다:
--   - V2(이 파일): 이력 테이블 생성 + backfill + 마스터의 레거시 컬럼을 NULL 허용으로 완화(엔티티는 더 이상 매핑하지 않음).
--   - V3        : 운영 안정화 후 레거시 컬럼(stor_file_nm/file_sz/file_hash) 실제 제거.
--
-- [공유 계약 안내]
--   OPTIMIZE(파이썬 최적화) 리비전은 파이썬 모듈이 물리 파일 저장과 이 테이블의 INSERT,
--   그리고 마스터 curr_rev_no 갱신까지 직접 수행한다. 채번/파일명 규칙은 doc/design/inp-file-revision.md 계약을 따른다.
--     - 다음 rev_no = (해당 파일의 max(rev_no)) + 1
--     - 저장 파일명 권장 규약 = {inp_file_id}_r{rev_no}.{확장자}  (stor_file_nm 컬럼이 SSOT)
--     - 리비전 추가 시 마스터 curr_rev_no 를 새 리비전으로 이동

-- 1) 이력 테이블 생성
CREATE TABLE inp_file_rev_h (
    rev_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '리비전 PK(대리키)',
    inp_file_id   VARCHAR(36)  NOT NULL COMMENT 'INP 파일 ID(마스터 inp_file_m FK)',
    rev_no        INT          NOT NULL COMMENT '리비전 번호(0부터 1씩 증가, max+1 채번)',
    stor_file_nm  VARCHAR(255) NOT NULL COMMENT '저장 파일명(권장 규약 {uuid}_r{rev_no}.{확장자})',
    file_sz       BIGINT       NOT NULL COMMENT '파일 크기(byte)',
    file_hash     VARCHAR(64)           COMMENT 'SHA-256 해시(무결성/중복감지, 선택)',
    work_type     VARCHAR(20)  NOT NULL COMMENT '작업구분(ORIGIN:업로드원본 / EDIT:웹편집 / OPTIMIZE:파이썬최적화)',
    rgst_dttm     DATETIME(6)  NOT NULL COMMENT '등록일시',
    PRIMARY KEY (rev_id),
    UNIQUE KEY uq_inp_file_rev (inp_file_id, rev_no),
    KEY idx_rev_file_id (inp_file_id),
    CONSTRAINT fk_rev_inp_file FOREIGN KEY (inp_file_id) REFERENCES inp_file_m (inp_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INP 파일 리비전 이력';

-- 2) 마스터에 현재 적용 리비전 포인터 추가 (기존 행은 0으로 채워짐)
ALTER TABLE inp_file_m
    ADD COLUMN curr_rev_no INT NOT NULL DEFAULT 0 COMMENT '현재 적용 리비전 번호(이력 테이블 포인터)';

-- 3) 기존 마스터 데이터를 rev0(ORIGIN) 으로 이력 테이블에 backfill
--    기존 물리 파일은 이름 변경 없이 그대로 rev0 의 저장 파일명으로 보존한다(Flyway 는 파일을 옮기지 않음).
INSERT INTO inp_file_rev_h (inp_file_id, rev_no, stor_file_nm, file_sz, file_hash, work_type, rgst_dttm)
SELECT inp_file_id, 0, stor_file_nm, file_sz, file_hash, 'ORIGIN', rgst_dttm
FROM inp_file_m;

-- 4) 리비전으로 이전된 레거시 컬럼을 NULL 허용으로 완화한다(엔티티는 더 이상 매핑하지 않으므로 신규 INSERT 가 통과되어야 함).
--    실제 컬럼 제거는 운영 안정화 후 V3 에서 수행한다.
ALTER TABLE inp_file_m
    MODIFY COLUMN stor_file_nm VARCHAR(255) NULL COMMENT '[DEPRECATED: V3 제거 예정] 리비전 테이블(inp_file_rev_h)로 이전',
    MODIFY COLUMN file_sz      BIGINT       NULL COMMENT '[DEPRECATED: V3 제거 예정] 리비전 테이블(inp_file_rev_h)로 이전',
    MODIFY COLUMN file_hash    VARCHAR(64)  NULL COMMENT '[DEPRECATED: V3 제거 예정] 리비전 테이블(inp_file_rev_h)로 이전';
