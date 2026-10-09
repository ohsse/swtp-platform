-- INP 파일 마스터에 모니터링 여부 컬럼을 추가한다.
-- 공유 DB(덤프/운영)에는 이미 컬럼이 존재할 수 있으므로 IF NOT EXISTS 로 멱등하게 추가한다.
-- 기존 _yn 컬럼(anal_yn/disp_yn)과 동일하게 char(1) 에 'Y'/'N' 으로 저장한다. 기본값 'N'(모니터링 안 함).
alter table inp_file_m
    add column if not exists mntr_yn char(1) default 'N' comment '모니터링여부';
