-- 펌프조합 마스터 테이블 (펌프 가동조건 조합식 상수)
-- 한 행 = 한 펌프조합(comb_id) = 한 성능곡선이다. 펌프조합별 성능곡선 회귀 계수와 유효 유량 구간,
-- 운영 지표(전력원단위 등)를 보관한다.
--
-- [성능곡선] 압력 회귀식 P(Q) = P_ADD_VAL·Q² + P_MUL_VAL·Q + P_SQRT_MUL_VAL 의 계수를 담는다.
--   레거시 EMS(TB_PUMP_CAL)의 add/mul/sqrt_mul 명칭은 실제 수학적 역할(2차항/1차항/상수항)과 일치하지 않으나,
--   공유 EMS 테이블이므로 컬럼명을 그대로 둔다(BE 조회 DTO 에서 quad/linear/const 로 재명명).
--   CS_OP/SS_OP(연산자)는 압력 계산식(레거시 pressureCalValue)에서 실제로 사용되지 않는다.
--   FC_MIN_VAL/FC_MAX_VAL 은 곡선의 유효 유량 구간(표본 조회 시 기본 구간)이다.
--
-- [기록 주체] 펌프조합·계수는 외부 EMS/AI 모듈이 공유 DB 에 직접 적재한다(BE 는 조회만, 쓰기 엔드포인트 없음).
--   따라서 공유 DB(덤프/운영)에 이미 테이블이 존재할 수 있으므로 CREATE TABLE IF NOT EXISTS 로 멱등하게 둔다.
CREATE TABLE IF NOT EXISTS pump_comb_m (
    comb_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '조합키PK(대리키)',
    P_ADD_VAL       DOUBLE       NOT NULL DEFAULT 0 COMMENT '압력추가(성능곡선 2차항 계수 a)',
    CS_OP           VARCHAR(5)   DEFAULT NULL COMMENT '배율및추가연산자(압력 계산식 미사용)',
    P_MUL_VAL       DOUBLE       NOT NULL DEFAULT 1 COMMENT '압력배율(성능곡선 1차항 계수 b)',
    SS_OP           VARCHAR(5)   DEFAULT NULL COMMENT '배율및제곱연산자(압력 계산식 미사용)',
    P_SQRT_MUL_VAL  DOUBLE       NOT NULL DEFAULT 1 COMMENT '압력제곱근배율(성능곡선 상수항 c)',
    PUMP_COMB       VARCHAR(30)  NOT NULL COMMENT '펌프조합(펌프IDX를 ,로 이어붙인 문자열)',
    PUMP_COUNT      DOUBLE       DEFAULT NULL COMMENT '펌프조합대수(운영대수, 가중값 가능)',
    FC_MIN_VAL      DOUBLE       DEFAULT NULL COMMENT '유량범위최소값(곡선 유효 최소 유량)',
    FC_MAX_VAL      DOUBLE       DEFAULT NULL COMMENT '유량범위최대값(곡선 유효 최대 유량)',
    PUMP_PRIORITY   INT          DEFAULT NULL COMMENT '우선순위',
    avg_error_rate  DOUBLE       DEFAULT NULL COMMENT '평균오차(율)',
    power_unit      DOUBLE       DEFAULT NULL COMMENT '전력원단위',
    power_cost_unit DOUBLE       DEFAULT NULL COMMENT '전력비원단위',
    PRIMARY KEY (comb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='펌프조합 마스터(펌프 가동조건 조합식 상수)';
