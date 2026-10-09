-- INP 파일 리비전(이력) 관리 도입 [2단계 마이그레이션 중 2단계]
-- V2 에서 NULL 허용으로 완화해 둔 레거시 컬럼을 실제로 제거한다.
--
-- 이 컬럼들은 리비전 도입 후 inp_file_rev_h 로 이전되어 더 이상 사용하지 않는다.
-- (저장 파일명/크기/해시는 '현재 적용 리비전' 기준으로 inp_file_rev_h 에서 조회한다.)
--
-- ⚠️ 비가역 작업이다. V2 적용 후 운영이 안정화되고 레거시 컬럼을 참조하는 외부 의존이 없음을 확인한 뒤 적용한다.
ALTER TABLE inp_file_m
    DROP COLUMN stor_file_nm,
    DROP COLUMN file_sz,
    DROP COLUMN file_hash;
