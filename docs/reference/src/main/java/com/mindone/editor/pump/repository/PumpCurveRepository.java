package com.mindone.editor.pump.repository;

import com.mindone.editor.pump.dto.PumpCurveCoef;
import com.mindone.editor.pump.dto.PumpCurveSaveRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.hibernate.query.TypedParameterValue;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 펌프 성능곡선 계수 조회/저장 저장소 (공유 EMS DB {@code pump_comb_m} 직접 접근).
 *
 * <p>{@code pump_comb_m} 은 Flyway/JPA 엔티티 관리 대상이 아닌 공유 EMS 테이블이라
 * ({@code ddl-auto: validate}) {@link EntityManager} 네이티브 쿼리로 접근한다
 * ({@link com.mindone.editor.pump.repository.PumpCombStatRepository} 와 동일한 방침).</p>
 *
 * <p>성능곡선 회귀 계수(P_ADD_VAL/P_MUL_VAL/P_SQRT_MUL_VAL)와 유효 유량 구간(FC_MIN_VAL/FC_MAX_VAL)을 읽고
 * ({@link #findCoefByCombId}), 갱신/추출 결과를 그 행에 저장한다({@link #updateCurve}). 한 {@code comb_id} =
 * 한 행 = 한 곡선이다.</p>
 */
@Repository
public class PumpCurveRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 펌프조합 1건의 성능곡선 계수 + 유효 유량 구간 조회 SQL.
     *
     * <p>파라미터: {@code :combId} 펌프조합 ID.</p>
     */
    private static final String CURVE_COEF_SQL = """
            SELECT comb_id           AS comb_id,
                   PUMP_COMB         AS pump_comb,
                   P_ADD_VAL         AS quad_coef,
                   P_MUL_VAL         AS linear_coef,
                   P_SQRT_MUL_VAL    AS const_coef,
                   FC_MIN_VAL        AS fc_min,
                   FC_MAX_VAL        AS fc_max
            FROM pump_comb_m
            WHERE comb_id = :combId
            """;

    /**
     * 펌프조합 1건의 성능곡선 계수·유량 구간·평가지표 저장(UPDATE) SQL.
     *
     * <p>파라미터는 모두 요청 passthrough 값이며, {@code comb_id} 기준으로 한 행을 갱신한다.
     * data_count 컬럼은 V17 마이그레이션으로 추가된 '데이터수(회귀 표본 수)' 컬럼이다.</p>
     */
    private static final String UPDATE_CURVE_SQL = """
            UPDATE pump_comb_m
            SET P_ADD_VAL       = :quadCoef,
                P_MUL_VAL       = :linearCoef,
                P_SQRT_MUL_VAL  = :constCoef,
                FC_MIN_VAL      = :fcMin,
                FC_MAX_VAL      = :fcMax,
                avg_error_rate  = :avgErrorRate,
                power_unit      = :powerUnit,
                power_cost_unit = :powerCostUnit,
                PUMP_PRIORITY   = :priority,
                data_count      = :dataCount
            WHERE comb_id = :combId
            """;

    /**
     * 펌프조합 ID 로 성능곡선 계수를 조회한다.
     *
     * @param combId 펌프조합 ID ({@code pump_comb_m.comb_id})
     * @return 계수 + 유효 유량 구간 (없으면 {@link Optional#empty()})
     */
    public Optional<PumpCurveCoef> findCoefByCombId(Long combId) {
        return em.createNativeQuery(CURVE_COEF_SQL, Tuple.class)
                .setParameter("combId", combId)
                .getResultList()
                .stream()
                .findFirst()
                .map(row -> toCoef((Tuple) row));
    }

    /**
     * 펌프조합 1건의 성능곡선 회귀 계수·유효 유량 구간·평가지표를 저장(UPDATE)한다.
     *
     * <p>회귀 계수·상수(quadCoef/linearCoef/constCoef)는 정밀도 보존을 위해 {@link BigDecimal} 로 받으며
     * ({@link PumpCurveSaveRequest}), nullable 참조타입이라 {@link TypedParameterValue} 로 타입을 명시해
     * 바인딩한다(네이티브 쿼리의 null 타입 추론 실패 방지). 매핑 대상 컬럼은 {@code DOUBLE} 이라 저장 시
     * double 로 좁혀지지만 계수 원천이 이미 {@code float64} 라 저장 정보 손실은 없다. nullable 정수
     * 컬럼(PUMP_PRIORITY/data_count)도 같은 이유로 감싸며, 나머지 지표는 원시 {@code double} 이라 항상 값이 있다.</p>
     *
     * @param combId  펌프조합 ID ({@code pump_comb_m.comb_id})
     * @param request 저장할 계수·유량 구간·평가지표(passthrough)
     * @return 갱신된 행 수(대상 {@code combId} 가 없으면 0)
     */
    public int updateCurve(Long combId, PumpCurveSaveRequest request) {
        return em.createNativeQuery(UPDATE_CURVE_SQL)
                .setParameter("quadCoef", new TypedParameterValue<>(StandardBasicTypes.BIG_DECIMAL, request.quadCoef()))
                .setParameter("linearCoef", new TypedParameterValue<>(StandardBasicTypes.BIG_DECIMAL, request.linearCoef()))
                .setParameter("constCoef", new TypedParameterValue<>(StandardBasicTypes.BIG_DECIMAL, request.constCoef()))
                .setParameter("fcMin", request.fcMin())
                .setParameter("fcMax", request.fcMax())
                .setParameter("avgErrorRate", request.avgErrorRate())
                .setParameter("powerUnit", request.powerUnit())
                .setParameter("powerCostUnit", request.powerCostUnit())
                .setParameter("priority", new TypedParameterValue<>(StandardBasicTypes.INTEGER, request.priority()))
                .setParameter("dataCount", new TypedParameterValue<>(StandardBasicTypes.INTEGER, request.dataCount()))
                .setParameter("combId", combId)
                .executeUpdate();
    }

    /** Tuple → 계수 DTO 매핑(숫자 컬럼은 DB 구현별 타입 차이를 흡수하기 위해 {@link Number} 로 변환). */
    private static PumpCurveCoef toCoef(Tuple t) {
        return new PumpCurveCoef(
                toLong(t.get("comb_id")),
                t.get("pump_comb", String.class),
                toDouble(t.get("quad_coef")),
                toDouble(t.get("linear_coef")),
                toDouble(t.get("const_coef")),
                toDouble(t.get("fc_min")),
                toDouble(t.get("fc_max"))
        );
    }

    /** 숫자형 Tuple 값을 double 로 변환(null 은 0.0). */
    private static double toDouble(Object value) {
        return (value == null) ? 0.0 : ((Number) value).doubleValue();
    }

    /** 숫자형 Tuple 값을 Long 으로 변환(null 보존). */
    private static Long toLong(Object value) {
        return (value == null) ? null : ((Number) value).longValue();
    }
}
