-- INP 분석 매핑에 분석여부(anal_yn) 컬럼을 추가한다.
-- 공유 DB(덤프/운영)에는 이미 컬럼이 존재할 수 있으므로 IF NOT EXISTS 로 멱등하게 추가한다.
alter table inp_anal_mapping
add column if not exists anal_yn char(1) default 'Y' comment '분석여부';
