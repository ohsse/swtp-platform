package com.mindone.editor.rawdata.repository;

import com.mindone.editor.rawdata.dto.RawDataLatestResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 실측 태그값 조회 저장소 (공유 EMS DB 직접 조회).
 *
 * <p>대상 테이블({@code TB_RAWDATA})은 Flyway/JPA 엔티티 관리 대상이 아닌 공유 EMS 테이블(파티셔닝된 운영 테이블)이라,
 * 엔티티 매핑 대신 {@link EntityManager} 네이티브 쿼리로 조회한다(같은 저장소의 펌프 실측 조회와 동일 방식).</p>
 *
 * <p><b>성능</b>: {@code (TAGNAME, TS)} 보조 인덱스를 타도록 태그 등치 + TS 범위 + TS 내림차순으로 구성해
 * 1건만 읽는다(LIMIT 1).</p>
 */
@Repository
public class RawDataRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 특정 태그의 기간 내 최신 실측값 1건 조회 SQL.
     *
     * <p>파라미터: {@code :tagNo} 태그번호(컬럼 {@code TAGNAME}), {@code :from}~{@code :to} 조회기간(TS, 양끝 포함).
     * {@code VALUE} 는 SQL 예약어와 겹치므로 별칭을 {@code val} 로 둔다.</p>
     */
    private static final String LATEST_SQL = """
            SELECT rd.TS AS ts, rd.VALUE AS val, rd.QUALITY AS quality
            FROM TB_RAWDATA rd
            WHERE rd.TAGNAME = :tagNo
              AND rd.TS >= :from
              AND rd.TS <= :to
            ORDER BY rd.TS DESC
            LIMIT 1
            """;

    /**
     * 특정 태그의 기간 내 가장 마지막(최신) 실측값 1건을 조회한다.
     *
     * @param tagNo 태그번호(SCADA 태그, 컬럼 {@code TAGNAME})
     * @param from  조회 시작(TS 이상)
     * @param to    조회 끝(TS 이하)
     * @return 최신 실측값(기간 내 계측이 없으면 {@link Optional#empty()})
     */
    public Optional<RawDataLatestResponse> findLatest(String tagNo, LocalDateTime from, LocalDateTime to) {
        List<Tuple> rows = em.createNativeQuery(LATEST_SQL, Tuple.class)
                .setParameter("tagNo", tagNo)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toResponse(tagNo, rows.get(0)));
    }

    /** Tuple → 응답 매핑. */
    private static RawDataLatestResponse toResponse(String tagNo, Tuple t) {
        return new RawDataLatestResponse(
                tagNo,
                toLocalDateTime(t.get("ts")),
                (String) t.get("val"),
                (String) t.get("quality")
        );
    }

    /** 타임스탬프형 Tuple 값을 LocalDateTime 으로 변환(드라이버별 타입 차이 흡수). */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }
}
