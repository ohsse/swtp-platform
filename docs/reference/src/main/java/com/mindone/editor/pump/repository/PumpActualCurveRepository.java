package com.mindone.editor.pump.repository;

import com.mindone.editor.pump.dto.PumpActualCurvePoint;
import com.mindone.editor.pump.dto.PumpCombInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 실측 펌프조합 성능곡선 조회 저장소 (공유 EMS DB 직접 집계).
 *
 * <p>대상 테이블({@code pump_comb_m}, {@code TB_CTR_PRF_PUMPMST_INF}, {@code TB_RAWDATA})은 Flyway/JPA 엔티티
 * 관리 대상이 아닌 공유 EMS 테이블이라, 엔티티 매핑 대신 {@link EntityManager} 네이티브 쿼리로 집계한다.</p>
 *
 * <h3>실측 성능곡선 구성</h3>
 * <ul>
 *   <li><b>대상 펌프</b>: {@code pump_comb_m.PUMP_COMB}(펌프IDX 콤마 문자열)를 {@code FIND_IN_SET} 으로 분해해
 *       해당 조합의 활성 펌프(USE_YN=1)를 모은다.</li>
 *   <li><b>유량 태그(flow)</b>: 그 펌프들의 {@code FRI_TAG} 을 <b>중복 제거</b>한다. FRI 는 펌프별이 아니라 그룹(헤더)
 *       공유 태그라, 조합이 두 그룹에 걸치면 그룹당 1개씩(예: 구 FRI-4001, 신 FRI-4004) 남는다. 분(TS)별로 이들을
 *       <b>합산</b>해 조합 전체 유량을 만든다.</li>
 *   <li><b>양정 태그(head)</b>: {@code PRI_S_TAG} 을 쓴다. 이 태그는 전 펌프가 공유하는 단일 공통 태그라
 *       조합/그룹과 무관하게 분당 하나의 값이다. 실측 <b>원시값을 단위 변환 없이</b> 그대로 양정으로 둔다.</li>
 *   <li><b>표본점</b>: 조회기간 {@code [from, to]} 의 매 분 중 유량·양정이 모두 존재하는 분을 (시각, 유량, 양정)
 *       점으로 만든다. TB_RAWDATA 를 한 번만 스캔하도록 태그 등가조인 + 분 단위 피벗으로 단일 패스 구성한다.</li>
 * </ul>
 *
 * <p><b>성능</b>: TB_RAWDATA 는 PK(TS,TAGNAME)만 있어 기간 범위 스캔이 비용의 대부분이다(대략 1일당 ~1초).
 * 조회기간이 길수록 선형 증가하므로 과도하게 긴 기간은 지양한다.</p>
 */
@Repository
public class PumpActualCurveRepository {

    @PersistenceContext
    private EntityManager em;

    /** 조합 존재/펌프 구성 확인용 — comb_id 의 PUMP_COMB 문자열 조회. */
    private static final String PUMP_COMB_SQL = """
            SELECT PUMP_COMB FROM pump_comb_m WHERE comb_id = :combId
            """;

    /** 성능곡선 갱신용 — comb_id 의 PUMP_COMB(파이썬 회귀 파라미터)·PUMP_PRIORITY(우선순위) 조회. */
    private static final String COMB_INFO_SQL = """
            SELECT PUMP_COMB AS pump_comb, PUMP_PRIORITY AS pump_priority
            FROM pump_comb_m WHERE comb_id = :combId
            """;

    /**
     * 실측 성능곡선 집계 SQL.
     *
     * <p>파라미터: {@code :combId} 펌프조합, {@code :from}~{@code :to} 조회기간(TS, 양끝 포함).</p>
     */
    private static final String ACTUAL_CURVE_SQL = """
            WITH comb AS (
                SELECT PUMP_COMB FROM pump_comb_m WHERE comb_id = :combId
            ),
            pumps AS (
                SELECT p.FRI_TAG, p.PRI_S_TAG
                FROM TB_CTR_PRF_PUMPMST_INF p
                JOIN comb c
                WHERE p.USE_YN = 1
                  AND FIND_IN_SET(p.PUMP_IDX, c.PUMP_COMB)
            ),
            tags AS (
                SELECT DISTINCT FRI_TAG AS tag, 'FRI' AS kind
                FROM pumps WHERE FRI_TAG IS NOT NULL AND FRI_TAG <> ''
                UNION
                SELECT DISTINCT PRI_S_TAG AS tag, 'PRI' AS kind
                FROM pumps WHERE PRI_S_TAG IS NOT NULL AND PRI_S_TAG <> ''
            ),
            pivot AS (
                SELECT
                    rd.TS AS ts,
                    SUM(CASE WHEN t.kind = 'FRI' THEN CAST(rd.VALUE AS DECIMAL(18,6)) END) AS flow,
                    MAX(CASE WHEN t.kind = 'PRI' THEN CAST(rd.VALUE AS DECIMAL(18,6)) END) AS head
                FROM TB_RAWDATA rd
                JOIN tags t ON t.tag = rd.TAGNAME
                WHERE rd.TS >= :from AND rd.TS <= :to
                GROUP BY rd.TS
            )
            SELECT flow, head
            FROM pivot
            WHERE flow IS NOT NULL AND head IS NOT NULL
            ORDER BY ts
            """;

    /**
     * 펌프조합의 PUMP_COMB(펌프IDX 콤마 문자열)를 조회한다. 조합 존재 확인용.
     *
     * @param combId 펌프조합 ID ({@code pump_comb_m.comb_id})
     * @return PUMP_COMB 문자열(없으면 {@link Optional#empty()})
     */
    public Optional<String> findPumpComb(Long combId) {
        List<?> rows = em.createNativeQuery(PUMP_COMB_SQL)
                .setParameter("combId", combId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0));
    }

    /**
     * 펌프조합의 갱신용 메타({@code PUMP_COMB} 문자열 + {@code PUMP_PRIORITY})를 조회한다.
     *
     * @param combId 펌프조합 ID ({@code pump_comb_m.comb_id})
     * @return 조합 메타(없으면 {@link Optional#empty()})
     */
    public Optional<PumpCombInfo> findCombInfo(Long combId) {
        return em.createNativeQuery(COMB_INFO_SQL, Tuple.class)
                .setParameter("combId", combId)
                .getResultList()
                .stream()
                .findFirst()
                .map(row -> {
                    Tuple t = (Tuple) row;
                    Object priority = t.get("pump_priority");
                    return new PumpCombInfo(
                            t.get("pump_comb", String.class),
                            priority == null ? null : ((Number) priority).intValue());
                });
    }

    /**
     * 펌프조합의 실측 성능곡선 표본점(유량, 양정)을 조회한다.
     *
     * @param combId 펌프조합 ID
     * @param from   조회 시작(TS 이상)
     * @param to     조회 끝(TS 이하)
     * @return 시각 오름차순 표본점 목록(유량·양정 모두 존재하는 분만)
     */
    public List<PumpActualCurvePoint> findActualCurve(Long combId, LocalDateTime from, LocalDateTime to) {
        List<Tuple> rows = em.createNativeQuery(ACTUAL_CURVE_SQL, Tuple.class)
                .setParameter("combId", combId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.stream().map(PumpActualCurveRepository::toPoint).toList();
    }

    /** Tuple → 표본점 매핑(숫자 컬럼은 DB 구현별 타입 차이를 흡수하기 위해 {@link Number} 로 변환). */
    private static PumpActualCurvePoint toPoint(Tuple t) {
        return PumpActualCurvePoint.of(
                toDouble(t.get("flow")),
                toDouble(t.get("head"))
        );
    }

    /** 숫자형 Tuple 값을 double 로 변환. */
    private static double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }
}
