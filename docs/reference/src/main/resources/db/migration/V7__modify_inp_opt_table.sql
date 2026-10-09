-- 공유 DB(덤프/운영)에는 이미 컬럼이 존재할 수 있으므로 IF NOT EXISTS 로 멱등하게 추가한다.
-- (rev_no 는 V5 의 테이블 정의에 이미 포함되어 있으므로 여기서 다시 추가하지 않는다.)
alter table inp_file_opt_h
    add column if not exists option_snap json default null comment '설정스냅샷';

alter table inp_file_opt_h
    add column if not exists result_snap json default null comment '결과스냅샷';

alter table inp_file_opt_h
    add column if not exists prev_result_snap json default null comment '이전결과스냅샷';

alter table inp_file_opt_h
    add column if not exists error_text text default null comment '에러내용';

alter table inp_file_opt_h
    add column if not exists prev_rev_no integer(11) default null comment '이전리비전번호';