-- 태그 예측 정확도 평가 테이블 (예측-실측 페어, 예측점 1행)
--
-- [목적] 화면 「알고리즘 예측 정확도 모니터링」 조회 시 from~to 구간을 raw(TB_RAWDATA, 1분 간격)에서
--   매번 역산하면 부하가 크다. 비싼 부분은 '예측점마다 실측 윈도 평균(a)을 구하는 것'이고,
--   오차지표(RMSE/MAE/MAPE/sMAPE)는 '예측점별 항의 합 ÷ n' 형태라 구간에 대해 가산적이다.
--   따라서 윈도 평균을 예측점당 1회만 계산해 이 테이블에 박아두고(아래 EVENT), 화면은 이 테이블만
--   GROUP BY duration_cd 로 단순 합산한다. per-point 그레인이라 임의 from~to·드래그 구간도 정확히 집계된다.
--
-- [정의]
--   p(예측값)            = tag_pred_l.pred_value
--   a(실측값)            = TB_RAWDATA 의 [crt_dttm, pred_dttm] 윈도 평균  (예: M10, 09:00 예측 → 08:50~09:00 평균)
--   abs_err = |a-p|, sq_err = (a-p)^2, ape = |(a-p)/a|*100 (a=0이면 NULL), sape = |a-p|/((|a|+|p|)/2)*100
--   → 파생항 4개는 a·p 의 결정적 함수이므로 STORED 생성컬럼으로 둔다(EVENT 는 a 만 채우면 자동 계산).
--
-- [기록 주체] 적재는 BE 가 아니라 MariaDB EVENT(스케줄러)가 수행한다(웹 API 에 스케줄러를 두지 않음).
--   공유 DB(덤프/운영)에 이미 존재할 수 있으므로 테이블은 CREATE TABLE IF NOT EXISTS 로 멱등하게 두고,
--   EVENT 는 CREATE OR REPLACE EVENT 로 둬서 정의가 바뀌면 항상 최신으로 덮어쓰도록 한다(드롭/ALTER 불필요).
CREATE TABLE IF NOT EXISTS tag_pred_eval_l (
    eval_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '평가 PK(대리키)',
    tag_no      VARCHAR(255) NOT NULL COMMENT '태그번호(SCADA 태그 = TB_RAWDATA.TAGNAME)',
    pred_dttm   DATETIME(6)  NOT NULL COMMENT '예측시간(예측 대상 시각)',
    duration_cd VARCHAR(10)  NOT NULL COMMENT '예측구간 코드[M10/M30/H1/H3/H6]',
    crt_dttm    DATETIME(6)  NOT NULL COMMENT '예측 생성시간(= 실측 윈도 시작, pred_dttm - duration)',
    pred_value  DOUBLE       NOT NULL COMMENT '예측값(p)',
    actual_avg  DOUBLE           NULL COMMENT '실측 윈도 평균(a). 윈도 내 유효 raw 가 없으면 NULL',
    sample_cnt  INT              NULL COMMENT '실측 윈도 내 표본 수',
    -- 파생 오차항(STORED 생성컬럼): a 가 NULL 이면 모두 NULL 로 계산되어 화면 AVG 집계에서 자동 제외된다.
    abs_err     DOUBLE AS (ABS(actual_avg - pred_value)) STORED COMMENT '절대오차 |a-p| (MAE용)',
    sq_err      DOUBLE AS (POW(actual_avg - pred_value, 2)) STORED COMMENT '제곱오차 (a-p)^2 (RMSE용)',
    ape         DOUBLE AS (CASE WHEN actual_avg = 0 THEN NULL
                                ELSE ABS((actual_avg - pred_value) / actual_avg) * 100 END) STORED
                                COMMENT '절대백분율오차 |(a-p)/a|*100 (MAPE용, a=0이면 NULL)',
    sape        DOUBLE AS (CASE WHEN (ABS(actual_avg) + ABS(pred_value)) = 0 THEN NULL
                                ELSE ABS(actual_avg - pred_value) / ((ABS(actual_avg) + ABS(pred_value)) / 2) * 100 END) STORED
                                COMMENT '대칭절대백분율오차 (sMAPE용)',
    eval_dttm   DATETIME(6)      NULL COMMENT '집계(평가) 수행 시각',
    PRIMARY KEY (eval_id),
    UNIQUE KEY uq_tag_pred_eval (tag_no, pred_dttm, duration_cd),
    KEY idx_tag_pred_eval_lookup (tag_no, duration_cd, pred_dttm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='태그 예측 정확도 평가(예측-실측 페어)';

-- 적재 스케줄러 (MariaDB EVENT)
-- [전제] 서버 변수 event_scheduler 가 ON 이어야 동작한다(현재 대상 DB 는 ON). 재시작 후에도 유지하려면
--   서버 설정(my.cnf 의 event_scheduler=ON)에 명시한다. 권한 결합을 피하려 SET GLOBAL 은 두지 않는다.
-- [동작] 매 10분마다, 예측시간이 지나 윈도가 완성된 최근 1일분 예측점에 대해 TB_RAWDATA 윈도 평균(a)을
--   구해 UPSERT 한다(최근 1일 재평가로 늦게 도착한 raw 보정 포함). 파생 오차항은 생성컬럼이 자동 계산한다.
-- [실행 위상] 예측이 매 정각/10분(HH:x0:00)에 생성되므로, STARTS 를 '10분 경계 + 30초' 위상으로 잡아
--   실행 시각이 매 HH:x0:30 에 떨어지게 한다(예측 적재 + 해당 분 raw 도착을 위한 30초 버퍼).
--   STARTS 가 과거 시각이어도 스케줄러가 다음 미래 실행으로 굴린다(ON COMPLETION PRESERVE 로 보존).
--   40초로 바꾸려면 STARTS 의 초만 :40 으로 수정한다.
-- [백필] 과거 전체를 한 번에 채우려면 아래 INSERT 본문에서 'pred_dttm > NOW() - INTERVAL 1 DAY' 조건만
--   제거해 1회 수동 실행한다.
-- [성능] TB_RAWDATA PK 는 (TS, TAGNAME) 이라 태그별 윈도 조회가 비효율적일 수 있다. 모니터링 태그 수가
--   많아지면 TB_RAWDATA 에 보조 인덱스 (TAGNAME, TS) 추가를 검토한다(공유 테이블이라 별도 합의 필요).
CREATE OR REPLACE EVENT ev_tag_pred_eval
ON SCHEDULE EVERY 10 MINUTE STARTS '2000-01-01 00:00:30'
ON COMPLETION PRESERVE
ENABLE
COMMENT '태그 예측 정확도 평가 적재(예측-실측 페어, 최근 1일 재평가)'
DO
INSERT INTO tag_pred_eval_l
    (tag_no, pred_dttm, duration_cd, crt_dttm, pred_value, actual_avg, sample_cnt, eval_dttm)
SELECT
    p.tag_no,
    p.pred_dttm,
    p.duration_cd,
    p.crt_dttm,
    p.pred_value,
    (SELECT AVG(CAST(r.VALUE AS DECIMAL(18, 4)))
       FROM TB_RAWDATA r
      WHERE r.TAGNAME = p.tag_no
        AND r.TS >= p.crt_dttm
        AND r.TS <= p.pred_dttm
        AND r.QUALITY = '100'),
    (SELECT COUNT(*)
       FROM TB_RAWDATA r
      WHERE r.TAGNAME = p.tag_no
        AND r.TS >= p.crt_dttm
        AND r.TS <= p.pred_dttm
        AND r.QUALITY = '100'),
    NOW(6)
FROM tag_pred_l p
WHERE p.pred_dttm <= NOW()
  AND p.pred_dttm >  NOW() - INTERVAL 1 DAY
ON DUPLICATE KEY UPDATE
    crt_dttm   = VALUES(crt_dttm),
    pred_value = VALUES(pred_value),
    actual_avg = VALUES(actual_avg),
    sample_cnt = VALUES(sample_cnt),
    eval_dttm  = VALUES(eval_dttm);
