-- 태그 예측값 테이블
-- SCADA 태그번호별 예측값을 저장한다. 한 (태그번호, 예측시간) 조합에 대해 예측 구간(duration) 5종
-- (M10=10분 / M30=30분 / H1=1시간 / H3=3시간 / H6=6시간)의 예측값이 각각 존재할 수 있다.
-- 즉 8시 예측데이터라면 같은 태그번호·같은 예측시간(8시)에 duration 5종의 행이 각각 한 건씩 존재한다.
--
-- [자연키] (태그번호, 예측시간, 예측구간)이 한 예측값을 유일하게 식별한다 → UNIQUE 로 중복을 차단한다.
--   같은 (태그번호, 예측시간)이라도 예측구간이 다르면 별개 행이므로 중복이 아니다.
--
-- [기록 주체] 외부 예측 모듈이 공유 DB·스토리지에 직접 INSERT/UPSERT 한다(BE 는 쓰기 엔드포인트 없이 조회만).
--   따라서 공유 DB(덤프/운영)에 이미 테이블이 존재할 수 있으므로 CREATE TABLE IF NOT EXISTS 로 멱등하게 둔다.
CREATE TABLE IF NOT EXISTS tag_pred_l (
    pred_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '예측값 PK(대리키)',
    tag_no      VARCHAR(255) NOT NULL COMMENT '태그번호(SCADA 태그)',
    pred_dttm   DATETIME(6)  NOT NULL COMMENT '예측시간(예측 대상 시각)',
    crt_dttm    DATETIME(6)  NOT NULL COMMENT '생성시간(예측 생성 시각)',
    duration_cd VARCHAR(10)  NOT NULL COMMENT '예측구간 코드[M10=10분/M30=30분/H1=1시간/H3=3시간/H6=6시간]',
    pred_value  DOUBLE       NOT NULL COMMENT '예측값',
    PRIMARY KEY (pred_id),
    UNIQUE KEY uq_tag_pred_l (tag_no, pred_dttm, duration_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='태그 예측값';
