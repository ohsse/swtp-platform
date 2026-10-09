-- 태그 헌팅값 보정 테이블 2종 + 보정 트리거
--
-- [목적] 계측 태그에 헌팅값(순간 튐/노이즈)이 원본값으로 들어오면, 미리 저장해 둔 태그별 평균값을
--   가져와 보정값에 채운다. 프론트는 보정 테이블을 5~10초 간격으로 폴링해
--   "태그번호 ~ 가 헌팅값 ~ 에서 평균보간을 사용해서 ~ 값으로 보정되었습니다." 토스트를 띄운다.
--
-- [정책] 전부 보정 - INSERT 되는 모든 원본값을 평균값으로 보정한다(임계치 조건 없음).
--
-- [기록 주체] 공유 DB(덤프/운영)에 이미 존재할 수 있으므로 CREATE TABLE IF NOT EXISTS 로 멱등하게 둔다.

-- 1) 태그별 평균값(보정 기준 마스터)
CREATE TABLE IF NOT EXISTS tag_avg_m (
    tag_no  VARCHAR(255) NOT NULL COMMENT '태그번호(SCADA 태그)',
    avg_val DOUBLE       NOT NULL COMMENT '태그 평균값(보정 기준)',
    reg_dt  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록일시',
    upd_dt  DATETIME(6)  NULL     ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (tag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='태그별 평균값(보정 기준 마스터)';

-- 2) 태그 계측값/보정 결과
--   [자연키] (계측시간, 태그번호)이 한 계측을 유일하게 식별한다.
--   corr_yn : 평균값을 찾아 보정을 완료했으면 Y (프론트가 이 행을 잡음)
--   noti_yn : 프론트가 토스트를 띄웠으면 Y (이미 알린 행 재알림 방지)
CREATE TABLE IF NOT EXISTS tag_meas_l (
    meas_ts  DATETIME(3)  NOT NULL COMMENT '계측시간(ms 단위)',
    tag_no   VARCHAR(255) NOT NULL COMMENT '태그번호(SCADA 태그)',
    raw_val  DOUBLE       NOT NULL COMMENT '원본값(입력된 헌팅값)',
    corr_val DOUBLE       NULL     COMMENT '보정값(평균보간 결과)',
    corr_yn  CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '보정여부[Y/N]',
    noti_yn  CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '토스트 알림 처리여부[Y/N]',
    PRIMARY KEY (meas_ts, tag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='태그 계측값 및 보정 결과';

-- 3) 보정 트리거
--   BEFORE INSERT 로 NEW 행을 직접 수정 → 추가 UPDATE 없이 한 번에 보정값을 채운다.
--   평균값이 존재하면 corr_val 에 평균값을 세팅하고 corr_yn = 'Y' 로 표시한다.
--   (해당 태그의 평균값이 없으면 corr_val 은 NULL, corr_yn 은 기본값 'N' 유지)
DROP TRIGGER IF EXISTS trg_tag_meas_corr;
CREATE TRIGGER trg_tag_meas_corr
BEFORE INSERT ON tag_meas_l
FOR EACH ROW
BEGIN
    SET NEW.corr_val = (SELECT avg_val FROM tag_avg_m WHERE tag_no = NEW.tag_no);
    IF NEW.corr_val IS NOT NULL THEN
        SET NEW.corr_yn = 'Y';
    END IF;
END;
