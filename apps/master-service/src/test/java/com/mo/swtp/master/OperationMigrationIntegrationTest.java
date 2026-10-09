package com.mo.swtp.master;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3__operation_drvmd_chg.sql이 실제 PostgreSQL에 만든 결과를 검증한다.
 *
 * <p>master-service는 두 스키마의 DDL을 소유한다 — 자기 도메인 {@code master}와,
 * 제어 이력 공용 write 스키마 {@code operation}(아키텍처 11.3의 명시적 예외)이다.
 * 이 클래스는 후자만 본다.
 *
 * <p><b>가장 중요한 단언은 첫 번째다.</b> {@code spring.flyway.default-schema}가 {@code master}라
 * V3이 스키마명을 하드코딩하지 않으면 테이블이 {@code master}에 만들어진다.
 * 그 실패는 조용하다 — 마이그레이션은 성공하고 테이블도 생기며 다만 스키마가 틀린다.
 */
@DisplayName("operation 운전모드 변경이력 마이그레이션")
class OperationMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String TABLE = "drvmd_chg_h";

    /** V3이 만드는 전 컬럼 — 순서까지 의도된 것이다(대상 → 전이 → 발행 → 사유 → 등록감사) */
    private static final List<String> COLUMNS = List.of(
            "hist_id",
            "ctrl_trgt_type_cd", "ctrl_trgt_id", "ctrl_trgt_nm",
            "bf_drvmd_cd", "af_drvmd_cd",
            "iss_svc_cd",
            "chg_rsn_cd", "chg_rsn_rmrk",
            "rgstr_dttm", "rgstr_id");

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("drvmd_chg_h는 operation 스키마에 생기고 master 스키마에는 없다")
    void 테이블이_operation_스키마에_생성된다() {
        var operationTables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'operation' AND table_type = 'BASE TABLE'
                         ORDER BY table_name
                        """)
                .query(String.class).list();

        // containsExactly 는 의도적이다 — 이 스키마에 예상하지 않은 테이블이 생기는 것까지 잡는다.
        // 아키텍처 11.3이 예고한 operation.control_command 가 들어오면 이 단언이 깨지는데,
        // 그때는 완화(contains)가 아니라 그 마이그레이션이 자기 검증과 함께 이 목록을 갱신해야 한다.
        // operation 은 여러 서비스가 write 하는 공용 스키마라, 무엇이 사는지를 좁게 못박아 두는 값이 크다.
        assertThat(operationTables)
                .as("operation 스키마의 테이블")
                .containsExactly(TABLE);

        // default-schema가 master라, V3이 스키마명을 빠뜨리면 여기에 생긴다.
        var masterTables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'master' AND table_name = ?
                        """)
                .param(TABLE)
                .query(String.class).list();

        assertThat(masterTables)
                .as("master 스키마에 잘못 생성되지 않았는가")
                .isEmpty();
    }

    @Test
    @DisplayName("Flyway 히스토리는 master 스키마 단독 — operation에는 생기지 않는다")
    void 히스토리_테이블이_master에만_있다() {
        var schemas = jdbc.sql("""
                        SELECT table_schema FROM information_schema.tables
                         WHERE table_name = 'flyway_schema_history'
                         ORDER BY table_schema
                        """)
                .query(String.class).list();

        assertThat(schemas).containsExactly("master");
    }

    @Test
    @DisplayName("컬럼 구성이 결정대로다 — ERD 원본에서 mdf_*를 빼고 4개를 더한 형태")
    void 컬럼_구성이_결정대로다() {
        assertThat(columnsInOrder()).containsExactlyElementsOf(COLUMNS);
    }

    @Test
    @DisplayName("PK는 hist_id 단독이고 자동 채번된다")
    void PK가_hist_id_단독이다() {
        var pkColumns = jdbc.sql("""
                        SELECT a.attname FROM pg_constraint c
                          JOIN pg_namespace n ON n.oid = c.connamespace
                          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
                         WHERE n.nspname = 'operation' AND c.contype = 'p'
                           AND c.conrelid = 'operation.drvmd_chg_h'::regclass
                         ORDER BY a.attnum
                        """)
                .query(String.class).list();
        assertThat(pkColumns).containsExactly("hist_id");

        var dataType = jdbc.sql("""
                        SELECT data_type FROM information_schema.columns
                         WHERE table_schema = 'operation' AND table_name = ?
                           AND column_name = 'hist_id'
                        """)
                .param(TABLE)
                .query(String.class).single();
        assertThat(dataType).isEqualTo("bigint");

        // BIGSERIAL은 bigint + 전용 시퀀스로 전개되고, 그 시퀀스도 테이블을 따라 operation에 놓인다.
        //
        // column_default 문자열을 보지 않는다 — pg_get_expr가 시퀀스명을 현재 search_path 기준으로
        // 렌더링하므로, search_path에 operation이 들어가는 순간 'operation.' 접두가 사라진다.
        // 01-schemas.sql이 이미 ALTER DATABASE ... SET search_path 를 쓰고 있어 실제로 움직이는 값이다.
        // 스키마가 완벽히 옳은데도 테스트가 깨지는 것을 막으려면 시퀀스의 소속을 직접 물어야 한다.
        var sequenceSchema = jdbc.sql("""
                        SELECT n.nspname FROM pg_class s
                          JOIN pg_namespace n ON n.oid = s.relnamespace
                         WHERE s.oid = pg_get_serial_sequence('operation.drvmd_chg_h', 'hist_id')::regclass
                        """)
                .query(String.class).single();
        assertThat(sequenceSchema).isEqualTo("operation");
    }

    @Test
    @DisplayName("2단계 PK 생성 후 _u_idx 는 남지 않고 _pkey 로 흡수된다")
    void PK_인덱스가_제약_이름으로_개명된다() {
        assertThat(indexNames())
                .contains("drvmd_chg_h_pkey")
                .doesNotContain("drvmd_chg_h_u_idx");
    }

    @Test
    @DisplayName("조회 인덱스가 (ctrl_trgt_id, rgstr_dttm DESC)로 걸려 있다")
    void 조회_인덱스가_시간_역순이다() {
        var indexDef = jdbc.sql("""
                        SELECT indexdef FROM pg_indexes
                         WHERE schemaname = 'operation' AND indexname = 'drvmd_chg_h_i_idx01'
                        """)
                .query(String.class).single();

        // 지배적 조회는 "사고 시각에 이 대상이 무슨 모드였나" — 대상 고정 + 시간 역순이다.
        // DESC가 빠져도 PG는 역방향 스캔이 가능해 성능이 곧장 무너지지는 않지만,
        // 의도가 사라지므로 정의 자체를 못박는다.
        //
        // 컬럼 순서까지 단언하는 이유: ctrl_trgt_type_cd 가 선두여야 한다.
        // ctrl_trgt_id 는 애플리케이션이 부여하는 값이라 공정과 제어그룹이 같은 ID를 가질 수 있고,
        // 판별자가 선두에서 빠지면 조회가 두 대상의 이력을 섞는다(결정 3의 근거 그대로).
        // 포함 여부만 보면 누가 판별자를 빼도 통과하므로 괄호 안 전체를 대조한다.
        assertThat(indexDef)
                .contains("(ctrl_trgt_type_cd, ctrl_trgt_id, rgstr_dttm DESC)")
                .doesNotContain("UNIQUE");
    }

    @Test
    @DisplayName("mdf_dttm/mdf_id를 두지 않는다 — append-only 이력이라는 선언")
    void 수정_감사_컬럼을_두지_않는다() {
        var columns = columnsInOrder();

        // 존재 단언을 먼저 둔다. 부재만 확인하면 테이블이 통째로 비어도 통과하는 공허 참이 된다
        // (ems 01 함정 5의 재현 방지).
        assertThat(columns).contains("rgstr_dttm", "rgstr_id");
        assertThat(columns).doesNotContain("mdf_dttm", "mdf_id");
    }

    @Test
    @DisplayName("chg_rsn_rmrk 하나만 NULL 허용이고 나머지는 전부 NOT NULL")
    void 비고만_NULL을_허용한다() {
        var nullable = jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                         WHERE table_schema = 'operation' AND table_name = ?
                           AND is_nullable = 'YES'
                         ORDER BY column_name
                        """)
                .param(TABLE)
                .query(String.class).list();

        // 사유 코드가 NOT NULL인 것이 결정 5의 핵심이다 — 사유 없는 이력을 DB가 거부한다.
        assertThat(nullable).containsExactly("chg_rsn_rmrk");
    }

    @Test
    @DisplayName("FK 제약을 걸지 않는다 — ctrl_trgt_id는 다형 참조라 FK 자체가 불가능하다")
    void FK_제약이_없다() {
        // 존재 앵커를 먼저 둔다 — "FK가 0건"만 보면 테이블이 아예 없어도 통과하는 공허 참이 된다.
        // 같은 클래스의 수정_감사_컬럼을_두지_않는다()는 그 방어를 하고 있는데 여기만 빠져 있었다.
        // ems 01 함정 5가 "한 테스트 클래스 안에서 단언 강도가 갈렸던 것"을 놓친 이유로 지목했다.
        // contype은 "char" 타입이라 ||로 바로 잇지 못한다 — text로 캐스팅해야 한다.
        var constraintsByType = jdbc.sql("""
                        SELECT c.contype::text || ':' || count(*) FROM pg_constraint c
                          JOIN pg_namespace n ON n.oid = c.connamespace
                         WHERE n.nspname = 'operation' AND c.contype IN ('p', 'f')
                         GROUP BY c.contype
                        """)
                .query(String.class).list();

        assertThat(constraintsByType).containsExactly("p:1");
    }

    @Test
    @DisplayName("테이블과 전 컬럼에 한글 코멘트가 달려 있다")
    void 한글_코멘트가_빠짐없이_달려_있다() {
        var tableComment = jdbc.sql("""
                        SELECT obj_description('operation.drvmd_chg_h'::regclass, 'pg_class')
                        """)
                .query(String.class).single();
        assertThat(tableComment).isNotNull().containsPattern("[가-힣]");

        var columnsWithoutComment = jdbc.sql("""
                        SELECT a.attname FROM pg_attribute a
                          JOIN pg_class c ON c.oid = a.attrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'operation' AND c.relkind = 'r'
                           AND a.attnum > 0 AND NOT a.attisdropped
                           AND col_description(c.oid, a.attnum) IS NULL
                        """)
                .query(String.class).list();
        assertThat(columnsWithoutComment).isEmpty();

        // "코멘트가 있다"와 "한글 코멘트가 있다"는 다르다 — 영문 코멘트도 위 단언은 통과한다.
        // 부정형("한글 아닌 것이 없다")으로 쓰지 않는다: 정규식이 아무것도 잡지 못해도 빈 결과라
        // 똑같이 통과한다. 한글을 가진 컬럼 수를 전체 컬럼 수와 대조하면 정규식이 실제로
        // 매칭하고 있다는 것까지 함께 증명된다 (ems 01 함정 6).
        assertThat(countColumns("col_description(c.oid, a.attnum) ~ '[가-힣]'"))
                .as("한글 코멘트를 가진 컬럼 수")
                .isEqualTo(countColumns("TRUE"))
                .isEqualTo(COLUMNS.size());
    }

    /**
     * {@code drvmd_chg_h}의 컬럼 중 {@code predicate}를 만족하는 개수.
     *
     * <p>스키마 전체가 아니라 이 테이블로 한정한다 — 개수를 {@link #COLUMNS}{@code .size()}와
     * 대조하는 쪽이라 다른 테이블이 섞이면 단언이 무너진다. 아키텍처 11.3이 같은 스키마에
     * {@code control_command}를 예고하고 있어 실제로 일어날 일이다.
     * 반면 "코멘트가 없는 컬럼 0건"은 스키마 전체를 그대로 본다 — 그쪽은 테이블이 늘어도 깨지지 않고,
     * 새 테이블이 코멘트 없이 들어오는 것을 잡는 그물로 남는다.
     */
    private int countColumns(String predicate) {
        return jdbc.sql("""
                        SELECT count(*) FROM pg_attribute a
                          JOIN pg_class c ON c.oid = a.attrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'operation' AND c.relkind = 'r'
                           AND c.relname = 'drvmd_chg_h'
                           AND a.attnum > 0 AND NOT a.attisdropped
                           AND %s
                        """.formatted(predicate))
                .query(Integer.class).single();
    }

    private List<String> columnsInOrder() {
        return jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                         WHERE table_schema = 'operation' AND table_name = ?
                         ORDER BY ordinal_position
                        """)
                .param(TABLE)
                .query(String.class).list();
    }

    private List<String> indexNames() {
        return jdbc.sql("""
                        SELECT indexname FROM pg_indexes
                         WHERE schemaname = 'operation' AND tablename = ?
                        """)
                .param(TABLE)
                .query(String.class).list();
    }
}
