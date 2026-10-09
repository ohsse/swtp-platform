-- 태그 예측 실행이력 테이블
-- 예측 모듈이 한 번 돌 때마다(배치 1회) 실행이력 1건을 남긴다. 실행 시작 시 RUNNING 으로 INSERT 하고,
-- 종료 시 종료시간·최종 상태(DONE/ERROR)·에러내용을 UPDATE 하는 식으로 진행 상태를 추적한다.
--
-- [예측시간(pred_dttm)] 이 실행이 산출한 예측의 기준(대상) 시각. tag_pred_l.pred_dttm 과 같은 의미로,
--   "어느 기준시각에 대한 예측 배치였는가" 를 가리킨다(실행시작/종료 시각과는 별개).
--
-- [실행상태(status_cd)] RUNNING=실행중 / DONE=완료 / ERROR=에러. duration_cd 와 동일하게 varchar 코드로 둔다.
--
-- [기록 주체] 외부 예측 모듈이 공유 DB 에 직접 INSERT/UPDATE 한다(BE 는 쓰기 엔드포인트 없이 조회만).
--   따라서 공유 DB(덤프/운영)에 이미 테이블이 존재할 수 있으므로 CREATE TABLE IF NOT EXISTS 로 멱등하게 둔다.
CREATE TABLE IF NOT EXISTS tag_pred_h (
    hist_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '이력번호 PK(대리키, 자동증가)',
    start_dttm DATETIME(6) NOT NULL COMMENT '실행시작시간',
    end_dttm   DATETIME(6)     NULL COMMENT '실행종료시간(실행중이면 NULL)',
    pred_dttm  DATETIME(6)     NULL COMMENT '예측시간(예측 기준 시각). 산출 전이면 NULL 일 수 있음',
    status_cd  VARCHAR(10) NOT NULL COMMENT '실행상태 코드[RUNNING=실행중/DONE=완료/ERROR=에러]',
    err_msg    TEXT            NULL COMMENT '에러내용(status_cd=ERROR 일 때)',
    PRIMARY KEY (hist_id),
    KEY idx_tag_pred_h_start (start_dttm),
    KEY idx_tag_pred_h_status (status_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='태그 예측 실행이력';
