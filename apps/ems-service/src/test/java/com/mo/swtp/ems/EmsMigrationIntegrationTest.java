package com.mo.swtp.ems;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1__ems_domain.sql이 실제 PostgreSQL에 만든 결과를 검증한다.
 *
 * <p>DDL은 컴파일러가 검사해 주지 않는다 — 오타나 규약 이탈은 운영 DB에 적용된 뒤에야 드러난다.
 * 그래서 "무엇이 참이면 마이그레이션이 옳은가"를 여기에 못박는다.
 * 특히 <b>단독키 PK</b>와 <b>감사 컬럼 유무</b>는 의도된 설계라 나중에 조용히 바뀌면 안 된다.
 */
@DisplayName("ems 제어그룹 도메인 마이그레이션")
class EmsMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> TABLES =
            List.of("ctrl_grp_m", "wnp_m", "ctrl_eqp_p", "ctrl_grp_wnp_p", "ctrl_grp_tag_p");

    /** {@code mdf_*}를 갖는 테이블 — {@code ctrl_eqp_p}만 빠진다(step-14: 수정하지 않으면 컬럼을 두지 않는다) */
    private static final List<String> MODIFIABLE_TABLES =
            List.of("ctrl_grp_m", "wnp_m", "ctrl_grp_wnp_p", "ctrl_grp_tag_p");

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("테이블 5종이 ems 스키마에 생성된다")
    void 테이블_5종이_생성된다() {
        var actual = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'ems' AND table_type = 'BASE TABLE'
                           AND table_name <> 'flyway_schema_history'
                         ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(actual).containsExactlyInAnyOrderElementsOf(TABLES);
    }

    @Test
    @DisplayName("Flyway 히스토리 테이블도 ems 스키마 안에 있다")
    void 히스토리_테이블이_자기_스키마에_있다() {
        var schemas = jdbc.sql("""
                        SELECT table_schema FROM information_schema.tables
                         WHERE table_name = 'flyway_schema_history'
                        """)
                .query(String.class).list();

        assertThat(schemas).containsExactly("ems");
    }

    @Test
    @DisplayName("PK는 ERD 원본대로 단독키다 — 설비·지점·태그 하나는 제어그룹 하나에만 속한다")
    void PK가_ERD_원본대로_단독키다() {
        assertThat(primaryKeyColumnsOf("ctrl_grp_m")).containsExactly("ctrl_grp_id");
        assertThat(primaryKeyColumnsOf("wnp_m")).containsExactly("wnp_id");
        assertThat(primaryKeyColumnsOf("ctrl_eqp_p")).containsExactly("eqp_id");
        assertThat(primaryKeyColumnsOf("ctrl_grp_wnp_p")).containsExactly("wnp_id");
        assertThat(primaryKeyColumnsOf("ctrl_grp_tag_p")).containsExactly("tag_sn");
    }

    @Test
    @DisplayName("2단계 PK 생성 후 _u_idx 는 남지 않고 _pkey 로 흡수된다")
    void PK_인덱스가_제약_이름으로_개명된다() {
        var indexNames = jdbc.sql("""
                        SELECT indexname FROM pg_indexes
                         WHERE schemaname = 'ems' AND tablename = ANY(?)
                        """)
                .param(TABLES.toArray(String[]::new))
                .query(String.class).list();

        assertThat(indexNames)
                .hasSize(TABLES.size())
                .allSatisfy(name -> assertThat(name).endsWith("_pkey"));
    }

    @Test
    @DisplayName("mdf_dttm/mdf_id는 있으면 NOT NULL이고, ctrl_eqp_p에는 아예 없다")
    void 감사_수정_컬럼_규약을_지킨다() {
        // 존재를 먼저 단언한다 — nullable 조회만으로는 컬럼이 통째로 사라져도 빈 결과라 공허 참이 된다
        for (String table : MODIFIABLE_TABLES) {
            assertThat(columnsOf(table))
                    .as("%s 의 수정 감사 컬럼", table)
                    .contains("mdf_dttm", "mdf_id");
        }

        // 수정하지 않는 테이블은 컬럼 자체를 두지 않는다(step-14)
        assertThat(columnsOf("ctrl_eqp_p")).doesNotContain("mdf_dttm", "mdf_id");

        var nullableModifyColumns = jdbc.sql("""
                        SELECT table_name || '.' || column_name FROM information_schema.columns
                         WHERE table_schema = 'ems' AND column_name IN ('mdf_dttm', 'mdf_id')
                           AND is_nullable = 'YES'
                        """)
                .query(String.class).list();
        assertThat(nullableModifyColumns).isEmpty();
    }

    @Test
    @DisplayName("등록 감사 컬럼은 5종 모두 NOT NULL로 존재한다")
    void 등록_감사_컬럼이_전_테이블에_있다() {
        for (String table : TABLES) {
            assertThat(columnsOf(table))
                    .as("%s 의 등록 감사 컬럼", table)
                    .contains("rgstr_dttm", "rgstr_id");
        }

        var nullableCreateColumns = jdbc.sql("""
                        SELECT table_name || '.' || column_name FROM information_schema.columns
                         WHERE table_schema = 'ems' AND column_name IN ('rgstr_dttm', 'rgstr_id')
                           AND is_nullable = 'YES'
                        """)
                .query(String.class).list();
        assertThat(nullableCreateColumns).isEmpty();
    }

    @Test
    @DisplayName("FK 제약을 걸지 않는다 — 참조 정합성은 애플리케이션 책임")
    void FK_제약이_없다() {
        var foreignKeys = jdbc.sql("""
                        SELECT c.conname FROM pg_constraint c
                          JOIN pg_namespace n ON n.oid = c.connamespace
                         WHERE n.nspname = 'ems' AND c.contype = 'f'
                        """)
                .query(String.class).list();

        assertThat(foreignKeys).isEmpty();
    }

    @Test
    @DisplayName("테이블과 전 컬럼에 한글 코멘트가 달려 있다")
    void 한글_코멘트가_빠짐없이_달려_있다() {
        var tablesWithoutComment = jdbc.sql("""
                        SELECT c.relname FROM pg_class c
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'ems' AND c.relkind = 'r'
                           AND c.relname <> 'flyway_schema_history'
                           AND obj_description(c.oid, 'pg_class') IS NULL
                        """)
                .query(String.class).list();
        assertThat(tablesWithoutComment).isEmpty();

        var columnsWithoutComment = jdbc.sql("""
                        SELECT c.relname || '.' || a.attname FROM pg_attribute a
                          JOIN pg_class c ON c.oid = a.attrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'ems' AND c.relkind = 'r'
                           AND a.attnum > 0 AND NOT a.attisdropped
                           AND c.relname <> 'flyway_schema_history'
                           AND col_description(c.oid, a.attnum) IS NULL
                        """)
                .query(String.class).list();
        assertThat(columnsWithoutComment).isEmpty();

        // "코멘트가 있다"와 "한글 코멘트가 있다"는 다르다 — 영문 코멘트도 위 단언은 통과한다.
        // 루트 불변식 7(주석·문서는 한국어)이 DDL 코멘트에도 적용되므로 한글 포함까지 단언한다.
        //
        // 부정형("한글 아닌 것이 없다")으로 쓰지 않는다 — 그 단언은 정규식이 아무것도 잡지 못할 때도
        // 빈 결과라 똑같이 통과한다. 한글을 가진 컬럼 수가 전체 컬럼 수와 같은지 대조하면
        // 정규식이 실제로 매칭하고 있다는 것까지 함께 증명된다.
        assertThat(countColumns("col_description(c.oid, a.attnum) ~ '[가-힣]'"))
                .as("한글 코멘트를 가진 컬럼 수")
                .isEqualTo(countColumns("TRUE"))
                .isGreaterThan(0);

        var koreanTableComments = jdbc.sql("""
                        SELECT c.relname FROM pg_class c
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'ems' AND c.relkind = 'r'
                           AND c.relname <> 'flyway_schema_history'
                           AND obj_description(c.oid, 'pg_class') ~ '[가-힣]'
                        """)
                .query(String.class).list();
        assertThat(koreanTableComments).containsExactlyInAnyOrderElementsOf(TABLES);
    }

    /** ems 스키마의 사용자 테이블 컬럼 중 {@code predicate}를 만족하는 개수 */
    private int countColumns(String predicate) {
        return jdbc.sql("""
                        SELECT count(*) FROM pg_attribute a
                          JOIN pg_class c ON c.oid = a.attrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'ems' AND c.relkind = 'r'
                           AND a.attnum > 0 AND NOT a.attisdropped
                           AND c.relname <> 'flyway_schema_history'
                           AND %s
                        """.formatted(predicate))
                .query(Integer.class).single();
    }

    @Test
    @DisplayName("use_yn 기본값 'Y'가 마스터 2종에 걸려 있다")
    void 사용여부_기본값이_걸려_있다() {
        var defaults = jdbc.sql("""
                        SELECT table_name FROM information_schema.columns
                         WHERE table_schema = 'ems' AND column_name = 'use_yn'
                           AND column_default LIKE '''Y''%'
                         ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(defaults).containsExactly("ctrl_grp_m", "wnp_m");
    }

    private List<String> primaryKeyColumnsOf(String table) {
        return jdbc.sql("""
                        SELECT a.attname FROM pg_constraint c
                          JOIN pg_namespace n ON n.oid = c.connamespace
                          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
                         WHERE n.nspname = 'ems' AND c.contype = 'p'
                           AND c.conrelid = ('ems.' || ?)::regclass
                         ORDER BY a.attnum
                        """)
                .param(table)
                .query(String.class).list();
    }

    private List<String> columnsOf(String table) {
        return jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                         WHERE table_schema = 'ems' AND table_name = ?
                        """)
                .param(table)
                .query(String.class).list();
    }
}
