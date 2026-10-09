package com.mo.swtp.master.drvmd.repository;

import com.mo.swtp.common.operation.ChangeReason;
import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.common.operation.DrivenMode;
import com.mo.swtp.common.operation.IssuerService;
import com.mo.swtp.master.drvmd.dto.DrvmdChgResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 운전모드 변경이력 조회 — {@code operation.drvmd_chg_h}.
 *
 * <p><b>조회 메서드만 둔다.</b> INSERT/UPDATE/DELETE 경로가 없는 것은 누락이 아니라 계약이다
 * (02 결정 1 · 04 결정 3). JPA 엔티티도 {@code JpaRepository}도 없으므로 {@code save()}가 존재하지 않는다.
 *
 * <p>엔티티가 아니므로 {@code JdbcClient}를 쓴다 — "엔티티가 아니면 JdbcClient, 엔티티면 JPQL"(04 결정 1).
 * telemetry·job이 같은 기준으로 JdbcClient를 쓰고 있다.
 */
@Repository
@RequiredArgsConstructor
public class DrvmdChgRepository {

    /**
     * SQL에 스키마명 {@code operation.}을 하드코딩한다.
     *
     * <p>job-service는 반대로 "SQL에 스키마명을 하드코딩하지 않는다"고 적어 두었는데, 그것은
     * {@code hikari.connection-init-sql}로 {@code search_path}가 잡힌 앱의 규칙이다.
     * master는 JPA 앱이라 {@code hibernate.default_schema: master}만 있고 {@code search_path}가 없다 —
     * JdbcClient는 그 설정의 혜택을 받지 못하므로 명시하지 않으면 {@code operation} 테이블을 찾지 못한다.
     */
    private static final String SELECT_COLUMNS = """
            SELECT hist_id, ctrl_trgt_type_cd, ctrl_trgt_id, ctrl_trgt_nm,
                   bf_drvmd_cd, af_drvmd_cd, iss_svc_cd,
                   chg_rsn_cd, chg_rsn_rmrk,
                   rgstr_dttm, rgstr_id
              FROM operation.drvmd_chg_h
             WHERE ctrl_trgt_type_cd = :ctrlTrgtType
               AND ctrl_trgt_id = :ctrlTrgtId""";

    /**
     * {@code rgstr_dttm} 역순이 지배적 조회이고 인덱스({@code drvmd_chg_h_i_idx01})가 그 형태다.
     *
     * <p>{@code hist_id DESC}를 tie-breaker로 덧붙인다 — 같은 밀리초에 두 행이 들어오면 정렬이
     * 비결정적이 되어 "그 시각의 모드"가 호출마다 달라질 수 있다. BIGSERIAL은 삽입 순서를 보존하므로
     * 나중에 들어온 행이 뒤의 상태다.
     */
    private static final String ORDER_BY_LATEST = " ORDER BY rgstr_dttm DESC, hist_id DESC";

    private final JdbcClient jdbcClient;

    /**
     * 대상별 이력 목록 — 최신순.
     *
     * <p>{@code from}/{@code to}는 선택이며 <b>비어 있으면 SQL 문장 자체에서 빠진다.</b>
     * {@code (:from IS NULL OR rgstr_dttm >= :from)} 형태를 쓰지 않는 이유는 그것이 PostgreSQL
     * 플래너의 generic plan에서 인덱스를 놓칠 수 있기 때문이다 — 이력 테이블은 무한 증가하므로
     * 인덱스가 생명이다(04 결정 2의 기각 사유와 같다).
     */
    public List<DrvmdChgResponse> findHistory(ControlTargetType ctrlTrgtType, String ctrlTrgtId,
                                              LocalDateTime from, LocalDateTime to, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ctrlTrgtType", ctrlTrgtType.name());
        params.put("ctrlTrgtId", ctrlTrgtId);

        if (from != null) {
            sql.append(" AND rgstr_dttm >= :from");
            params.put("from", from);
        }
        if (to != null) {
            sql.append(" AND rgstr_dttm <= :to");
            params.put("to", to);
        }
        sql.append(ORDER_BY_LATEST).append(" LIMIT :limit");
        params.put("limit", limit);

        return jdbcClient.sql(sql.toString()).params(params).query(ROW_MAPPER).list();
    }

    /**
     * {@code at} 시각 이하의 가장 최근 이력 1행 — "그때 이 대상이 무슨 모드였나"의 답.
     *
     * <p>비어 있으면 그 시점까지 한 번도 바뀐 적이 없다는 뜻이고, 그때의 모드는 초기값
     * {@link DrivenMode#AI_ANLS}다(03 결정 5). 그 판단은 서비스 계층이 한다.
     */
    public Optional<DrvmdChgResponse> findLatestAt(ControlTargetType ctrlTrgtType, String ctrlTrgtId,
                                                   LocalDateTime at) {
        return jdbcClient.sql(SELECT_COLUMNS + " AND rgstr_dttm <= :at" + ORDER_BY_LATEST + " LIMIT 1")
                .param("ctrlTrgtType", ctrlTrgtType.name())
                .param("ctrlTrgtId", ctrlTrgtId)
                .param("at", at)
                .query(ROW_MAPPER)
                .optional();
    }

    private static final RowMapper<DrvmdChgResponse> ROW_MAPPER = (rs, rowNum) -> {
        long histId = rs.getLong("hist_id");
        return new DrvmdChgResponse(
                histId,
                code(ControlTargetType.class, rs.getString("ctrl_trgt_type_cd"), "ctrl_trgt_type_cd", histId),
                rs.getString("ctrl_trgt_id"),
                rs.getString("ctrl_trgt_nm"),
                code(DrivenMode.class, rs.getString("bf_drvmd_cd"), "bf_drvmd_cd", histId),
                code(DrivenMode.class, rs.getString("af_drvmd_cd"), "af_drvmd_cd", histId),
                code(IssuerService.class, rs.getString("iss_svc_cd"), "iss_svc_cd", histId),
                code(ChangeReason.class, rs.getString("chg_rsn_cd"), "chg_rsn_cd", histId),
                rs.getString("chg_rsn_rmrk"),
                rs.getObject("rgstr_dttm", LocalDateTime.class),
                rs.getString("rgstr_id"));
    };

    /**
     * 코드 컬럼을 enum으로 바꾼다 — <b>실패를 조용히 넘기지 않는다</b>(04 결정 6).
     *
     * <p>V3에 CHECK 제약이 없고 FK도 없어 DB는 어떤 문자열이든 받는다(02 결정 5 · 03 한계 2).
     * 그물은 이 메서드뿐이므로, 걸렸을 때 <b>어느 행의 어느 컬럼에 어떤 값이 있었는지</b>를 남긴다.
     * {@code null}로 대체하지 않는다 — 감사 로그에서 조용한 손실은 오염보다 나쁘다.
     */
    private static <E extends Enum<E>> E code(Class<E> type, String value, String column, long histId) {
        if (value == null) {
            throw new IllegalStateException(
                    "operation.drvmd_chg_h hist_id=%d 의 %s 가 NULL이다 — NOT NULL 컬럼이므로 DDL과 데이터가 어긋났다"
                            .formatted(histId, column));
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "operation.drvmd_chg_h hist_id=%d 의 %s 에 코드 체계에 없는 값이 있다: '%s' (허용: %s)"
                            .formatted(histId, column, value, Arrays.toString(type.getEnumConstants())), e);
        }
    }
}
