-- INP 매핑 테이블을 시각화 전용으로 전환한다.
--   1) inp_mapping → inp_vis_mapping 으로 테이블명 변경 (분석용 → 시각화용)
--   2) anal_yn(분석여부) 컬럼 제거 (분석 기능이 빠지며 의미 상실)
-- (pipe_id 컬럼은 시각화 매핑에서도 계속 사용하므로 유지한다.)
--
-- 공유 DB(덤프/운영)에는 이미 최종 스키마가 반영돼 있을 수 있으므로 전 구간을 멱등하게 작성한다.

-- 1) 테이블명 변경: MariaDB 의 RENAME TABLE 은 IF EXISTS 를 지원하지 않으므로,
--    원본 테이블이 있고 대상 테이블이 아직 없을 때만 동적으로 실행한다(이미 변경됐으면 no-op).
SET @rename_sql = (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inp_mapping')
        AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inp_vis_mapping'),
        'RENAME TABLE inp_mapping TO inp_vis_mapping',
        'DO 0'
    )
);
PREPARE rename_stmt FROM @rename_sql;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;

-- 2) anal_yn(분석여부) 컬럼 제거 (멱등)
ALTER TABLE inp_vis_mapping DROP COLUMN IF EXISTS anal_yn;
