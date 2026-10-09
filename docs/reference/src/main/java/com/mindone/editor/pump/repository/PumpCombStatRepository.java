package com.mindone.editor.pump.repository;

import com.mindone.editor.pump.dto.PumpCombStatResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 펌프조합별 운영 현황 / 전력 원단위 조회 저장소.
 *
 * <p>대상 테이블은 Flyway/JPA 엔티티 관리 대상이 아닌 공유 EMS 테이블({@code pump_comb_m}) 이라,
 * 엔티티 매핑({@code ddl-auto: validate}) 대신 {@link EntityManager} 네이티브 쿼리로 조회한다.</p>
 *
 * <p>운영건수(분)·전력원단위 등 통계값은 외부 EMS/AI 프로세스가 {@code pump_comb_m} 에 미리 적재한다.
 * 따라서 이 조회는 조회 시점에 {@code TB_RAWDATA} 를 집계하지 않고(과거에는 기간 집계로 무거웠음),
 * {@code pump_comb_m} 에 저장된 값을 단순 SELECT 해 표출한다.</p>
 */
@Repository
public class PumpCombStatRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 펌프조합별 운영 현황 조회 SQL.
     *
     * <p>{@code pump_comb_m} 의 조합(PUMP_COMB&lt;&gt;'')에 대해 <b>전체 컬럼</b>을 그대로 표출한다(프론트가 운영 현황
     * 외에 성능곡선·평가지표 등 다른 화면도 그릴 수 있도록). 운영건수(분)·전력원단위 등 통계값은 외부 프로세스가 적재한
     * 저장값이다(미적재 시 NULL). 회귀 계수는 코드베이스 규약대로 별칭을 quad/linear/const 로 둔다.</p>
     */
    private static final String COMB_STAT_SQL = """
            SELECT
                comb_id         AS comb_id,
                PUMP_COMB       AS pump_comb,
                PUMP_COUNT      AS pump_count,
                PUMP_PRIORITY   AS pump_priority,
                P_ADD_VAL       AS quad_coef,
                P_MUL_VAL       AS linear_coef,
                P_SQRT_MUL_VAL  AS const_coef,
                FC_MIN_VAL      AS fc_min,
                FC_MAX_VAL      AS fc_max,
                CS_OP           AS cs_op,
                SS_OP           AS ss_op,
                run_minutes     AS run_minutes,
                data_count      AS data_count,
                avg_error_rate  AS avg_error_rate,
                power_unit      AS power_unit,
                power_cost_unit AS power_cost_unit
            FROM pump_comb_m
            WHERE PUMP_COMB <> ''
            ORDER BY PUMP_COUNT, PUMP_COMB, comb_id
            """;

    /**
     * 펌프조합별 운영 현황 / 전력 원단위를 조회한다.
     *
     * @return 조합별 행 목록 (운영대수→조합 순, 조합 전체)
     */
    public List<PumpCombStatResponse> findCombStats() {
        List<Tuple> rows = em.createNativeQuery(COMB_STAT_SQL, Tuple.class)
                .getResultList();

        return rows.stream().map(PumpCombStatRepository::toResponse).toList();
    }

    /** Tuple → 응답 DTO 매핑(숫자 컬럼은 DB 구현별 타입 차이를 흡수하기 위해 {@link Number} 로 변환). */
    private static PumpCombStatResponse toResponse(Tuple t) {
        return new PumpCombStatResponse(
                toLong(t.get("comb_id")),
                t.get("pump_comb", String.class),
                toDouble(t.get("pump_count")),
                toInteger(t.get("pump_priority")),
                toDouble(t.get("quad_coef")),
                toDouble(t.get("linear_coef")),
                toDouble(t.get("const_coef")),
                toDouble(t.get("fc_min")),
                toDouble(t.get("fc_max")),
                t.get("cs_op", String.class),
                t.get("ss_op", String.class),
                toLong(t.get("run_minutes")),
                toLong(t.get("data_count")),
                toDouble(t.get("avg_error_rate")),
                toDouble(t.get("power_unit")),
                toDouble(t.get("power_cost_unit"))
        );
    }

    /** 숫자형 Tuple 값을 Double 로 변환(null 보존). */
    private static Double toDouble(Object value) {
        return (value == null) ? null : ((Number) value).doubleValue();
    }

    /** 숫자형 Tuple 값을 Long 으로 변환(null 보존). */
    private static Long toLong(Object value) {
        return (value == null) ? null : ((Number) value).longValue();
    }

    /** 숫자형 Tuple 값을 Integer 로 변환(null 보존). */
    private static Integer toInteger(Object value) {
        return (value == null) ? null : ((Number) value).intValue();
    }
}
