-- 파이썬 OPTIMIZE 모듈이 공유 DB에 직접 생성·기록하는 테이블이므로,
-- 이미 존재하는 환경(덤프/운영 공유 DB)에서도 깨지지 않도록 IF NOT EXISTS 로 멱등하게 둔다.
-- 테이블이 없는 신규 환경에서만 아래 정의로 생성된다.
create table if not exists inp_file_opt_h (
    hist_id bigint(20) not null auto_increment comment '이력ID PK(대리키)',
    inp_file_id varchar(36) not null comment 'INP파일 ID',
    rev_no integer(11) not null comment '개정번호',
    strt_dttm timestamp not null default current_timestamp comment '시작일시',
    end_dttm timestamp null default null comment '종료일시',
    tot_gener_count int(11) not null comment '총세대수',
    impl_gener_count int(11) default null comment '진행세대수',
    status_cd varchar(30) not null comment '상태코드[RUNNING, COMPLETED, ERROR]',
    primary key (hist_id),
    key idx_opt_file_id (inp_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INP 파일 최적화 이력';