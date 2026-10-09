-- 펌프조합 마스터(pump_comb_m)에 데이터수(회귀 표본 수) 컬럼을 추가한다.
--   data_count = 성능곡선 회귀에 실제로 쓰인 실측 표본 수(파이썬 회귀 결과 data_count).
--   기존 avg_error_rate/power_unit/power_cost_unit/run_minutes 와 같은 '평가지표 저장용' 컬럼이며,
--   운영건수(분)인 run_minutes 와는 의미가 다르다(그쪽은 조합이 가동된 분 합, 이쪽은 회귀 표본 수).
--
-- [기록 주체] 기존 컬럼들은 외부 EMS/AI 모듈이 적재했으나, data_count 는 BE 의 성능곡선 저장
--   엔드포인트(PUT /pump-combinations/{combId}/performance-curve)가 갱신/추출 결과를 반영하며 쓴다.
-- 공유 DB(덤프/운영)에는 이미 컬럼이 존재할 수 있으므로 IF NOT EXISTS 로 멱등하게 추가한다.
alter table pump_comb_m
add column if not exists data_count int default null comment '데이터수(성능곡선 회귀에 쓰인 실측 표본 수)';
