package com.mo.swtp.ems;

import java.util.List;
import java.util.Map;

import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpEqpReplaceRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpTagReplaceRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpWnpReplaceRequest;
import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.starter.security.test.SwtpTestJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제어그룹 편성 슬라이스 통합 검증.
 *
 * <p><b>이 테스트가 지키는 것은 동작이지 구현 선택이 아니다.</b> 원리상 Hibernate는 flush 시
 * {@code insertions → deletions} 순으로 실행하므로 delete-then-insert가 PK 위반을 낼 수 있고,
 * 편성 저장은 거의 항상 "대부분 그대로 두고 몇 개만 들고 나는" 형태라 PK가 겹치는 것이 정상 경로다.
 * <b>다만 이 경로에서 그 위험이 실제로 발현되지는 않는다</b> — 벌크 DELETE를 파생 삭제 메서드로
 * 바꿔도 전부 통과한다(문서 03 「함정 기록」 1). 그러므로 여기서 초록이라는 것은
 * "겹치는 재저장이 동작한다"까지이고, 벌크 DELETE가 필수라는 뜻이 아니다.
 *
 * <p>{@code rgstr_dttm} 관련 단언들은 <b>행이 실제로 새로 만들어졌는지</b>를 본다.
 * merge 경로가 기존 행을 UPDATE로 재활용하면 {@code updatable = false} 때문에 그 값이
 * 옛 시각에 얼어붙고 예외는 나지 않는다 — 그 조용한 실패를 잡는 것이 이 단언들의 몫이다.
 */
@DisplayName("ems 제어그룹 편성 슬라이스")
class CtrlGrpMemberSliceIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BASE = "/api/ems/ctrl-grp";
    private static final String TESTER = "member-tester";

    private static final String GROUP_A = "MG-A";
    private static final String GROUP_B = "MG-B";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void 편성과_그룹을_초기화한다() {
        // 편성 3종은 이 클래스만 쓴다. ctrl_grp_m은 CtrlGrpSliceIntegrationTest와 공유하므로
        // 이 클래스가 만드는 MG- 접두사 행만 지운다 — 그쪽 픽스처(CG-)를 건드리지 않기 위해서다.
        jdbc.sql("DELETE FROM ems.ctrl_eqp_p").update();
        jdbc.sql("DELETE FROM ems.ctrl_grp_wnp_p").update();
        jdbc.sql("DELETE FROM ems.ctrl_grp_tag_p").update();
        jdbc.sql("DELETE FROM ems.ctrl_grp_m WHERE ctrl_grp_id LIKE 'MG-%'").update();

        addGroup(GROUP_A, "1계열 송수펌프", UseYn.Y);
        addGroup(GROUP_B, "2계열 송수펌프", UseYn.Y);
    }

    @Test
    @DisplayName("겹치는 멤버를 포함해 재저장해도 PK 충돌 없이 끝난다 — delete-then-insert flush 순서")
    void 겹치는_재저장이_성공한다() {
        replaceEqp(GROUP_A, List.of("EQP-1", "EQP-2", "EQP-3"));

        // EQP-1·EQP-2는 그대로 남고 EQP-3이 빠지고 EQP-4가 들어온다 —
        // 삭제와 저장이 같은 트랜잭션에서 같은 PK를 다루는 정상 경로다.
        JsonNode data = data(replaceEqp(GROUP_A, List.of("EQP-1", "EQP-2", "EQP-4")));

        assertThat(memberIds(data.path("members"))).containsExactly("EQP-1", "EQP-2", "EQP-4");
        assertThat(memberIds(data(get(BASE + "/" + GROUP_A + "/eqp")))).containsExactly("EQP-1", "EQP-2", "EQP-4");
        // 빠진 EQP-3이 어느 그룹에도 남아 있지 않다
        assertThat(rowCount("ctrl_eqp_p")).isEqualTo(3);
    }

    @Test
    @DisplayName("재저장하면 rgstr_dttm이 갱신된다 — 행이 재활용되지 않고 새로 만들어진다")
    void 재저장이_행을_새로_만든다() {
        replaceEqp(GROUP_A, List.of("EQP-1"));
        Object firstRegisteredAt = registeredAt("EQP-1");

        replaceEqp(GROUP_A, List.of("EQP-1"));

        // 삭제가 실제로 일어나지 않고 merge가 기존 행을 UPDATE로 재활용하면
        // rgstr_dttm은 updatable=false라 옛 값에 멈춘 채 아무 예외도 나지 않는다.
        assertThat(registeredAt("EQP-1")).isNotEqualTo(firstRegisteredAt);
    }

    @Test
    @DisplayName("다른 그룹의 설비를 뺏어오고, 어디서 빠졌는지를 응답에 싣는다")
    void 뺏어오기가_출처를_보고한다() {
        replaceEqp(GROUP_A, List.of("EQP-1", "EQP-2"));

        JsonNode data = data(replaceEqp(GROUP_B, List.of("EQP-2")));

        assertThat(memberIds(data.path("members"))).containsExactly("EQP-2");
        // "소리 없이 빠지는 것"을 막는 장치다 — 도메인 관점이 409를 주장한 우려에 대한 완화책
        assertThat(data.path("stolen")).hasSize(1);
        assertThat(data.path("stolen").get(0).path("memberId").asString()).isEqualTo("EQP-2");
        assertThat(data.path("stolen").get(0).path("fromCtrlGrpId").asString()).isEqualTo(GROUP_A);

        // A그룹은 실제로 한 대 줄었다
        assertThat(memberIds(data(get(BASE + "/" + GROUP_A + "/eqp")))).containsExactly("EQP-1");
    }

    @Test
    @DisplayName("뺏어온 편성은 새 행으로 만들어진다 — 남의 그룹 행이 조용히 UPDATE되지 않는다")
    void 뺏어온_편성은_새로_만들어진다() {
        replaceEqp(GROUP_A, List.of("EQP-1"));
        Object registeredInA = registeredAt("EQP-1");

        replaceEqp(GROUP_B, List.of("EQP-1"));

        // ID 기준 삭제가 없으면 merge가 A그룹 행을 찾아 ctrl_grp_id만 바꾸고,
        // rgstr_* 는 updatable=false라 A에 편성되던 시각에 얼어붙는다 — 예외 없이.
        // 그 조용한 실패를 잡는 것이 이 단언이고, 「결정 6」②의 실제 근거다.
        assertThat(registeredAt("EQP-1")).isNotEqualTo(registeredInA);
        assertThat(jdbc.sql("SELECT ctrl_grp_id FROM ems.ctrl_eqp_p WHERE eqp_id = 'EQP-1'")
                .query(String.class).single()).isEqualTo(GROUP_B);
    }

    @Test
    @DisplayName("자기 그룹 안에서만 재배치하면 stolen이 비어 있다")
    void 자기_그룹_재배치는_뺏어오기가_아니다() {
        replaceEqp(GROUP_A, List.of("EQP-1", "EQP-2"));

        JsonNode data = data(replaceEqp(GROUP_A, List.of("EQP-2", "EQP-1")));

        assertThat(data.path("stolen").isArray()).isTrue();
        assertThat(data.path("stolen")).isEmpty();
        // 순서가 뒤집혔다 — 재정렬이 전용 API 없이 replace-all로 해결된다
        assertThat(memberIds(data.path("members"))).containsExactly("EQP-2", "EQP-1");
    }

    @Test
    @DisplayName("빈 배열 저장이 편성을 비운다 — 비활성 그룹이 잡고 있던 설비를 푸는 수단")
    void 빈_배열이_편성을_비운다() {
        replaceEqp(GROUP_A, List.of("EQP-1", "EQP-2"));

        JsonNode data = data(replaceEqp(GROUP_A, List.of()));

        // isArray()를 먼저 건다 — path()는 키가 없으면 MissingNode를 돌려주고 그것도 "비어 있음"이라
        // isEmpty()만 걸면 members 필드가 통째로 사라져도 통과하는 공허 참이 된다
        assertThat(data.path("members").isArray()).isTrue();
        assertThat(data.path("members")).isEmpty();
        assertThat(rowCount("ctrl_eqp_p")).isZero();
    }

    @Test
    @DisplayName("비활성 그룹에도 편성을 저장할 수 있다 — 인질을 풀 길을 막지 않는다")
    void 비활성_그룹도_편성할_수_있다() {
        addGroup("MG-OFF", "정지된 계열", UseYn.N);

        ResponseEntity<String> response = replaceEqp("MG-OFF", List.of("EQP-9"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberIds(data(get(BASE + "/MG-OFF/eqp")))).containsExactly("EQP-9");

        // 그리고 그 인질을 다른 그룹이 뺏어올 수 있다
        JsonNode moved = data(replaceEqp(GROUP_A, List.of("EQP-9")));
        assertThat(moved.path("stolen").get(0).path("fromCtrlGrpId").asString()).isEqualTo("MG-OFF");
    }

    @Test
    @DisplayName("없는 그룹에 편성하면 404다")
    void 없는_그룹은_404다() {
        ResponseEntity<String> response = replaceEqp("MG-없음", List.of("EQP-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-404");
        assertThat(rowCount("ctrl_eqp_p")).isZero();
    }

    @Test
    @DisplayName("sort_ord가 요청 배열 순서로 1부터 매겨진다")
    void 정렬순서는_요청_순서다() {
        replaceEqp(GROUP_A, List.of("EQP-C", "EQP-A", "EQP-B"));

        // 응답·조회가 아니라 DB의 실제 컬럼값을 본다
        assertThat(jdbc.sql("SELECT sort_ord FROM ems.ctrl_eqp_p WHERE eqp_id = 'EQP-C'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT sort_ord FROM ems.ctrl_eqp_p WHERE eqp_id = 'EQP-A'")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT sort_ord FROM ems.ctrl_eqp_p WHERE eqp_id = 'EQP-B'")
                .query(Integer.class).single()).isEqualTo(3);
        // 조회 순서도 그 값을 따른다 — 등록 순서나 ID 순서가 아니다
        assertThat(memberIds(data(get(BASE + "/" + GROUP_A + "/eqp"))))
                .containsExactly("EQP-C", "EQP-A", "EQP-B");
    }

    @Test
    @DisplayName("ctrl_eqp_p는 mdf_* 없이 INSERT되고 rgstr_*가 토큰 주체로 채워진다")
    void 등록_전용_감사_컬럼이_채워진다() {
        replaceEqp(GROUP_A, List.of("EQP-1"));

        Map<String, Object> row = jdbc.sql(
                        "SELECT rgstr_dttm, rgstr_id FROM ems.ctrl_eqp_p WHERE eqp_id = 'EQP-1'")
                .query().singleRow();

        assertThat(row.get("rgstr_dttm")).isNotNull();
        assertThat(row.get("rgstr_id")).isEqualTo(TESTER);

        // 이 테이블에는 mdf_* 컬럼이 아예 없다 — BaseCreatedEntity 상속이 옳았다는 실행 증거
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM information_schema.columns
                         WHERE table_schema = 'ems' AND table_name = 'ctrl_eqp_p'
                           AND column_name IN ('mdf_dttm','mdf_id')
                        """).query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("mdf_*를 가진 편성 테이블은 등록값과 같은 값으로 채워진다 — 결정 4가 감수한 대가")
    void 수정_감사_컬럼은_등록값과_같다() {
        replaceWnp(GROUP_A, List.of("WNP-1"));

        Map<String, Object> row = jdbc.sql("""
                        SELECT rgstr_dttm, rgstr_id, mdf_dttm, mdf_id
                          FROM ems.ctrl_grp_wnp_p WHERE wnp_id = 'WNP-1'
                        """).query().singleRow();

        // delete-then-insert라 UPDATE가 일어나지 않으므로 이 두 컬럼은 영원히 등록값과 같다.
        // 규약 위반은 아니지만 "무의미한 컬럼 두 개"가 남은 상태이므로 문서에 남겼다.
        assertThat(row.get("mdf_dttm")).isEqualTo(row.get("rgstr_dttm"));
        assertThat(row.get("mdf_id")).isEqualTo(row.get("rgstr_id"));
    }

    @Test
    @DisplayName("요청 안에 같은 대상이 두 번 오면 409다")
    void 요청_내부_중복은_409다() {
        ResponseEntity<String> response = replaceEqp(GROUP_A, List.of("EQP-1", "EQP-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("EMS-409");
        assertThat(rowCount("ctrl_eqp_p")).isZero();
    }

    @Test
    @DisplayName("수계통지점·태그도 같은 규칙으로 동작한다 — 세 자원이 한 코드 경로다")
    void 세_자원이_같은_규칙이다() {
        replaceWnp(GROUP_A, List.of("WNP-1", "WNP-2"));
        replaceTag(GROUP_A, List.of("TAG-1", "TAG-2"));

        // 겹치는 재저장 — 교체 응답은 배열이 아니라 {members, stolen} 객체다
        assertThat(memberIds(data(replaceWnp(GROUP_A, List.of("WNP-1", "WNP-3"))).path("members")))
                .containsExactly("WNP-1", "WNP-3");
        assertThat(memberIds(data(replaceTag(GROUP_A, List.of("TAG-2", "TAG-1"))).path("members")))
                .containsExactly("TAG-2", "TAG-1");

        // 뺏어오기
        JsonNode stolenWnp = data(replaceWnp(GROUP_B, List.of("WNP-1")));
        assertThat(stolenWnp.path("stolen").get(0).path("fromCtrlGrpId").asString()).isEqualTo(GROUP_A);

        JsonNode stolenTag = data(replaceTag(GROUP_B, List.of("TAG-1")));
        assertThat(stolenTag.path("stolen").get(0).path("fromCtrlGrpId").asString()).isEqualTo(GROUP_A);
    }

    @Test
    @DisplayName("편성 API도 토큰 없이 호출하면 401이다")
    void 토큰이_없으면_401이다() {
        assertThat(rest.getForEntity(BASE + "/" + GROUP_A + "/eqp", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> put = rest.exchange(BASE + "/" + GROUP_A + "/eqp", HttpMethod.PUT,
                new HttpEntity<>(new CtrlGrpEqpReplaceRequest(List.of("EQP-1")), jsonHeaders()),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("본문에 배열이 없으면 400이다 — null과 빈 배열은 다르다")
    void 배열_누락은_400이다() {
        ResponseEntity<String> response = rest.exchange(BASE + "/" + GROUP_A + "/eqp", HttpMethod.PUT,
                authed(Map.of()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-400");
    }

    // ── 보조 ──────────────────────────────────────────────────────

    private void addGroup(String ctrlGrpId, String name, UseYn useYn) {
        ResponseEntity<String> response = rest.exchange(BASE, HttpMethod.POST,
                authed(new CtrlGrpAddRequest(ctrlGrpId, name, useYn, null)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> replaceEqp(String ctrlGrpId, List<String> eqpIds) {
        return rest.exchange(BASE + "/" + ctrlGrpId + "/eqp", HttpMethod.PUT,
                authed(new CtrlGrpEqpReplaceRequest(eqpIds)), String.class);
    }

    private ResponseEntity<String> replaceWnp(String ctrlGrpId, List<String> wnpIds) {
        return rest.exchange(BASE + "/" + ctrlGrpId + "/wnp", HttpMethod.PUT,
                authed(new CtrlGrpWnpReplaceRequest(wnpIds)), String.class);
    }

    private ResponseEntity<String> replaceTag(String ctrlGrpId, List<String> tagSns) {
        return rest.exchange(BASE + "/" + ctrlGrpId + "/tag", HttpMethod.PUT,
                authed(new CtrlGrpTagReplaceRequest(tagSns)), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.exchange(path, HttpMethod.GET, authed(null), String.class);
    }

    private static JsonNode data(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody()).path("data");
    }

    private static List<String> memberIds(JsonNode arrayNode) {
        assertThat(arrayNode.isArray()).isTrue();
        return arrayNode.valueStream().map(node -> node.path("memberId").asString()).toList();
    }

    private long rowCount(String table) {
        return jdbc.sql("SELECT count(*) FROM ems." + table).query(Long.class).single();
    }

    private Object registeredAt(String eqpId) {
        return jdbc.sql("SELECT rgstr_dttm FROM ems.ctrl_eqp_p WHERE eqp_id = ?")
                .param(eqpId).query().singleRow().get("rgstr_dttm");
    }

    private static HttpEntity<Object> authed(Object body) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(SwtpTestJwt.accessToken(TESTER, "편성 테스터", "ADMIN"));
        return new HttpEntity<>(body, headers);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
