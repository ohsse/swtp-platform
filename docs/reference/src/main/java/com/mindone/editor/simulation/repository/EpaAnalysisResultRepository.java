package com.mindone.editor.simulation.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 해석결과 조회 저장소 (공유 EMS DB 직접 조회).
 *
 * <p>{@code TB_EPA_TAG_INFO}(표시대상 위치·유량/압력 태그)와 {@code TB_RAWDATA}(계측값)를 조인해,
 * 표시대상({@code IS_DISPLAY = 1}) 위치를 {@code DISPLAY_ORDER} 순으로 계측값과 함께 조회한다.
 * 세 테이블 모두 Flyway/JPA 엔티티 관리 대상이 아닌 공유 EMS 테이블이라 {@link EntityManager} 네이티브 쿼리로 조회한다.</p>
 *
 * <p><b>시각 매칭</b>: 분석일시(분 단위)와 {@code TS} 를 정확히 등치({@code =})로 맞춰 파티션 프루닝을 태운다.</p>
 * <p><b>결측</b>: 유량/압력 태그를 각각 LEFT JOIN 하므로, 태그가 없거나 해당 분 계측이 없는 값은 {@code null} 로 들어온다.</p>
 */
@Repository
public class EpaAnalysisResultRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 표시대상 위치별 유량/압력 계측값 조회 SQL.
     *
     * <p>{@code VALUE} 는 SQL 예약어와 겹치므로 별칭({@code flowRaw}/{@code pressRaw})을 둔다.
     * 값 변환·반올림은 서비스에서 처리하므로 원본 문자열 그대로 가져온다.</p>
     */
    private static final String SQL = """
            SELECT e.LOCATION_NM AS name,
                   f.VALUE       AS flowRaw,
                   p.VALUE       AS pressRaw
            FROM TB_EPA_TAG_INFO e
            LEFT JOIN TB_RAWDATA f
                   ON f.TAGNAME = e.FRI_TAG
                  AND f.TS = :ts
            LEFT JOIN TB_RAWDATA p
                   ON p.TAGNAME = e.PRI_TAG
                  AND p.TS = :ts
            WHERE e.IS_DISPLAY = 1
            ORDER BY e.DISPLAY_ORDER
            """;

    /**
     * 표시대상 위치별 유량/압력 원시 계측값 목록을 {@code DISPLAY_ORDER} 순으로 조회한다.
     *
     * @param ts 분석일시(분 단위, 초=00)
     * @return 위치별 원시 계측 행 목록
     */
    public List<EpaMeasuredRow> findMeasuredRows(LocalDateTime ts) {
        List<Tuple> rows = em.createNativeQuery(SQL, Tuple.class)
                .setParameter("ts", ts)
                .getResultList();
        return rows.stream()
                .map(t -> new EpaMeasuredRow(
                        (String) t.get("name"),
                        (String) t.get("flowRaw"),
                        (String) t.get("pressRaw")))
                .toList();
    }
}
