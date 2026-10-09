-- 펌프조합 마스터(pump_comb_m)에 운영건수[분] 컬럼을 추가한다.
--   운영건수(분) = 해당 조합이 정확히 가동된 분의 합(조합 펌프 전부 ON + 나머지 OFF, TB_RAWDATA 1분 간격).
--   기존 power_unit/power_cost_unit/avg_error_rate 와 동일한 '계산결과 저장용' 컬럼이며, 적재 주체는
--   외부 EMS/AI 모듈이다(BE 는 조회만, 쓰기 엔드포인트 없음).
-- 공유 DB(덤프/운영)에는 이미 컬럼이 존재할 수 있으므로 IF NOT EXISTS 로 멱등하게 추가한다.
alter table pump_comb_m
add column if not exists run_minutes bigint default null comment '운영건수(분) — 해당 조합이 정확히 가동된 분의 합';
