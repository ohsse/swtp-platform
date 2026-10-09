package com.mindone.editor.simulation.repository;

import com.mindone.editor.simulation.dto.NodeDemand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관망해석 시뮬레이션 분석시각 조회 저장소 (공유 EMS DB 직접 조회).
 *
 * <p>대상 테이블({@code TB_RAWDATA}, {@code tb_pump_comb_rst}, {@code anal_node_tag_m})은 Flyway/JPA 엔티티
 * 관리 대상이 아닌 공유 EMS 테이블이라, 엔티티 매핑 대신 {@link EntityManager} 네이티브 쿼리로 조회한다
 * (같은 저장소의 {@code RawDataRepository}/{@code PumpActualCurveRepository} 와 동일 방식).</p>
 *
 * <p><b>시각 매칭</b>: 분석일시(분 단위)와 {@code TS}/{@code time} 을 정확히 등치({@code =})로 맞춘다.</p>
 * <p><b>값 변환</b>: {@code TB_RAWDATA.VALUE} 는 varchar 이므로 {@code DECIMAL} 로 캐스팅 후 정수로 반올림한다.</p>
 */
@Repository
public class SimulationAnalysisRepository {

    @PersistenceContext
    private EntityManager em;

    /** 특정 태그의 해당 분 계측값(정수 반올림) 조회 SQL. */
    private static final String OUT_FLOW_SQL = """
            SELECT ROUND(CAST(rd.VALUE AS DECIMAL(18, 6))) AS val
            FROM TB_RAWDATA rd
            WHERE rd.TAGNAME = :tagNo
              AND rd.TS = :ts
            LIMIT 1
            """;

    /** 해당 분의 펌프조합({@code act_comb}) 조회 SQL. {@code time} 은 MariaDB 예약어라 백틱으로 감싼다. */
    private static final String PUMP_COMB_SQL = """
            SELECT r.act_comb AS actComb
            FROM tb_pump_comb_rst r
            WHERE r.`ts` = :ts
            LIMIT 1
            """;

    /**
     * 노드별 수요량 조회 SQL.
     *
     * <p>{@code anal_node_tag_m} 의 각 노드를 태그({@code tag_no})로 {@code TB_RAWDATA} 에 LEFT JOIN 해,
     * 해당 분 계측이 없는 노드도 {@code demand} 를 {@code null} 로 포함한다.</p>
     */
    private static final String DEMANDS_SQL = """
            SELECT n.name AS name,
                   ROUND(CAST(rd.VALUE AS DECIMAL(18, 6))) AS demand
            FROM anal_node_tag_m n
            LEFT JOIN TB_RAWDATA rd
                   ON rd.TAGNAME = n.tag_no
                  AND rd.TS = :ts
            ORDER BY n.name
            """;

    /**
     * 정수장 송수유량 조회.
     *
     * @param tagNo 송수유량 태그번호
     * @param ts    분석일시(분 단위, 초=00)
     * @return 정수로 반올림한 유량(해당 분 계측이 없으면 {@code null})
     */
    public Long findOutFlow(String tagNo, LocalDateTime ts) {
        List<Tuple> rows = em.createNativeQuery(OUT_FLOW_SQL, Tuple.class)
                .setParameter("tagNo", tagNo)
                .setParameter("ts", ts)
                .getResultList();
        return rows.isEmpty() ? null : toLong(rows.get(0).get("val"));
    }

    /**
     * 펌프조합({@code act_comb}) 조회.
     *
     * @param ts 분석일시(분 단위)
     * @return 펌프조합 문자열(해당 분 결과가 없으면 {@code null})
     */
    public String findPumpComb(LocalDateTime ts) {
        List<Tuple> rows = em.createNativeQuery(PUMP_COMB_SQL, Tuple.class)
                .setParameter("ts", ts)
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0).get("actComb");
    }

    /**
     * 노드별 수요량 목록 조회.
     *
     * @param ts 분석일시(분 단위)
     * @return 노드별 수요량 목록(각 노드의 계측이 없으면 {@code demand} 는 {@code null})
     */
    public List<NodeDemand> findDemands(LocalDateTime ts) {
        List<Tuple> rows = em.createNativeQuery(DEMANDS_SQL, Tuple.class)
                .setParameter("ts", ts)
                .getResultList();
        return rows.stream()
                .map(t -> new NodeDemand((String) t.get("name"), toLong(t.get("demand"))))
                .toList();
    }

    /** 숫자형 Tuple 값을 {@link Long} 으로 변환(드라이버별 타입 차이 흡수, null 보존). */
    private static Long toLong(Object value) {
        return (value instanceof Number n) ? n.longValue() : null;
    }
}
