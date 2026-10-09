package com.mo.swtp.ems;

import java.util.List;
import java.util.Map;

import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyRequest;
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
 * 제어그룹 슬라이스 통합 검증 — 엔티티가 실제 PostgreSQL과 왕복하는 것을 처음으로 시험한다.
 *
 * <p>이 테스트가 만들어지기 전까지 이 리포지토리에는 <b>JPA 엔티티를 직접 저장·조회하는 앱 테스트가
 * 하나도 없었다.</b> master-service의 엔티티 6종은 실행 경로 자체가 없고,
 * auth-service의 슬라이스 테스트는 HTTP를 통해 엔티티를 넣지만 감사 주체가 {@code SYSTEM}이며
 * ({@code @Enumerated}도 assigned-ID도 쓰지 않는다) 아래 세 가지를 밟지 않는다:
 *
 * <ol>
 *   <li>{@code CHARACTER(1)} 컬럼에 {@code @Enumerated(EnumType.STRING)}으로 매핑한 enum의 왕복</li>
 *   <li>assigned-ID에서 {@code save()}가 {@code merge()}를 타 <b>등록이 조용한 수정이 되는 것</b></li>
 *   <li>인증된 요청의 토큰 {@code sub}가 {@code rgstr_id}·{@code mdf_id}에 실제로 박히는 것</li>
 * </ol>
 *
 * <p>REST 응답만 보지 않고 {@link JdbcClient}로 raw 컬럼값을 함께 확인한다 —
 * JPA가 읽고 쓰는 값이 같으면 매핑이 틀려도 응답만으로는 드러나지 않기 때문이다.
 */
@DisplayName("ems 제어그룹 슬라이스")
class CtrlGrpSliceIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BASE = "/api/ems/ctrl-grp";
    private static final String TESTER = "tester";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    /**
     * 매 테스트를 빈 테이블에서 시작한다.
     *
     * <p>컨테이너가 JVM 하나에 하나뿐이라 클래스·메서드 사이에 데이터가 남는다.
     * "빈 목록이 200"을 단언하려면 실제로 비어 있어야 하고, 정렬 단언도 남은 행이 섞이면 흔들린다.
     *
     * <p><b>{@code ctrl_grp_m}은 편성 테스트({@code CtrlGrpMemberSliceIntegrationTest})와 공유한다.</b>
     * 여기서 통째로 비우는 것이 지금 안전한 이유는 클래스가 순차 실행되고 그쪽도
     * {@code @BeforeEach}에서 자기 픽스처를 다시 세우기 때문이지, 이 테이블을 이 클래스만 쓰기 때문이 아니다.
     * 병렬 실행을 켜거나 {@code @BeforeAll}로 옮기면 이 전제가 무너진다.
     */
    @BeforeEach
    void 테이블을_비운다() {
        jdbc.sql("DELETE FROM ems.ctrl_grp_m").update();
    }

    @Test
    @DisplayName("등록한 use_yn이 DB에 'Y'로 저장되고 다시 enum으로 읽힌다 — CHARACTER(1) 왕복")
    void 사용여부가_bpchar로_왕복한다() {
        add(new CtrlGrpAddRequest("CG-001", "1계열 송수펌프", UseYn.N, 10));

        // DB에 실제로 들어간 문자를 본다 — 응답만 보면 JPA가 자기가 쓴 값을 그대로 돌려줄 뿐이다.
        // CHARACTER(1)이므로 남는 자리가 없어 패딩 공백이 붙지 않아야 한다.
        String raw = jdbc.sql("SELECT use_yn FROM ems.ctrl_grp_m WHERE ctrl_grp_id = 'CG-001'")
                .query(String.class).single();
        assertThat(raw).isEqualTo("N");

        JsonNode found = data(get(BASE + "/CG-001"));
        assertThat(found.path("useYn").asString()).isEqualTo("N");
        assertThat(found.path("ctrlGrpNm").asString()).isEqualTo("1계열 송수펌프");
        assertThat(found.path("sortOrd").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("사용여부를 생략하면 Y로 등록된다 — DDL DEFAULT가 아니라 엔티티가 채운다")
    void 사용여부_기본값은_Y다() {
        JsonNode created = data(add(new CtrlGrpAddRequest("CG-002", "2계열 송수펌프", null, null)));

        assertThat(created.path("useYn").asString()).isEqualTo("Y");
        // sort_ord는 nullable이라 그대로 비어 있어야 한다 — 0으로 채워지면 정렬이 왜곡된다
        assertThat(created.path("sortOrd").isNull()).isTrue();
    }

    @Test
    @DisplayName("이미 있는 ID로 등록하면 409다 — 조용한 UPDATE로 삼켜지지 않는다")
    void 중복_등록은_409로_끊긴다() {
        add(new CtrlGrpAddRequest("CG-003", "최초 등록", UseYn.Y, 1));
        Map<String, Object> before = auditColumnsOf("CG-003");

        ResponseEntity<String> again = rest.exchange(BASE, HttpMethod.POST,
                authed(new CtrlGrpAddRequest("CG-003", "덮어쓰기 시도", UseYn.N, 99)), String.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(JSON.readTree(again.getBody()).path("code").asString()).isEqualTo("EMS-409");

        // 409를 받았다는 것만으로는 부족하다 — 행이 그대로인지까지 봐야 "조용한 수정"이 없었음을 안다.
        // save()가 merge()를 타면 rgstr_dttm은 updatable=false라 옛 값에 멈춘 채 내용만 바뀐다.
        assertThat(auditColumnsOf("CG-003")).isEqualTo(before);
        assertThat(data(get(BASE + "/CG-003")).path("ctrlGrpNm").asString()).isEqualTo("최초 등록");
    }

    @Test
    @DisplayName("한 요청 안에 같은 ID가 두 번 오면 409다 — DB에 없어 존재 검사를 통과해 버린다")
    void 요청_내부_중복도_409다() {
        var body = Map.of("requests", List.of(
                new CtrlGrpAddRequest("CG-004", "앞", UseYn.Y, 1),
                new CtrlGrpAddRequest("CG-004", "뒤", UseYn.N, 2)));

        ResponseEntity<String> response = rest.exchange(BASE + "/list", HttpMethod.POST,
                authed(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("감사 주체가 토큰 sub로 채워진다 — SYSTEM이 아니다")
    void 감사_주체가_토큰_주체다() {
        add(new CtrlGrpAddRequest("CG-005", "감사 확인", UseYn.Y, 1));

        Map<String, Object> audit = auditColumnsOf("CG-005");
        assertThat(audit.get("rgstr_id")).isEqualTo(TESTER);
        assertThat(audit.get("mdf_id")).isEqualTo(TESTER);
    }

    @Test
    @DisplayName("등록 직후 mdf_*는 rgstr_*와 같고, 수정하면 달라진다 — step-14 판정 규칙")
    void 감사_컬럼이_규약대로_움직인다() {
        add(new CtrlGrpAddRequest("CG-006", "수정 전", UseYn.Y, 1));

        Map<String, Object> onInsert = auditColumnsOf("CG-006");
        // "한 번도 수정되지 않음"은 이 동등성으로 판정한다 — 느슨한 비교로 바꾸면 규약이 검증되지 않는다
        assertThat(onInsert.get("mdf_dttm")).isEqualTo(onInsert.get("rgstr_dttm"));
        assertThat(onInsert.get("mdf_id")).isEqualTo(onInsert.get("rgstr_id"));

        ResponseEntity<String> modified = rest.exchange(BASE + "/CG-006", HttpMethod.PUT,
                authed(new CtrlGrpModifyRequest("수정 후", null, null)), String.class);
        assertThat(modified.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> onUpdate = auditColumnsOf("CG-006");
        assertThat(onUpdate.get("mdf_dttm")).isNotEqualTo(onInsert.get("mdf_dttm"));
        // 등록 컬럼은 updatable=false라 수정에도 그대로여야 한다
        assertThat(onUpdate.get("rgstr_dttm")).isEqualTo(onInsert.get("rgstr_dttm"));

        JsonNode found = data(get(BASE + "/CG-006"));
        assertThat(found.path("ctrlGrpNm").asString()).isEqualTo("수정 후");
        // 부분 수정 — 보내지 않은 필드는 그대로다
        assertThat(found.path("useYn").asString()).isEqualTo("Y");
        assertThat(found.path("sortOrd").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정 응답의 mdf_*가 갱신된 값이다 — DB만 맞고 응답이 옛 값인 상태를 막는다")
    void 수정_응답이_갱신된_감사값을_싣는다() {
        add(new CtrlGrpAddRequest("CG-010", "수정 전", UseYn.Y, 1));
        String registeredAt = data(get(BASE + "/CG-010")).path("mdfDttm").asString();

        JsonNode modified = data(rest.exchange(BASE + "/CG-010", HttpMethod.PUT,
                authed(new CtrlGrpModifyRequest("수정 후", null, null)), String.class));

        // 감사 컬럼을 채우는 @PreUpdate는 flush 시점에 발화한다. flush 전에 응답을 만들면
        // DB는 옳은데 응답만 옛 값인 상태가 되고, DB를 읽는 단언으로는 절대 잡히지 않는다.
        assertThat(modified.path("mdfDttm").asString()).isNotEqualTo(registeredAt);
        assertThat(modified.path("mdfId").asString()).isEqualTo(TESTER);
        // 응답이 갱신됐다는 것과 DB가 갱신됐다는 것은 다른 주장이다 — 둘 다 확인한다.
        // (문자열 포맷이 달라 값끼리 직접 비교하지 않고, 각자 "등록 시점과 달라졌는가"로 본다)
        Map<String, Object> audit = auditColumnsOf("CG-010");
        assertThat(audit.get("mdf_dttm")).isNotEqualTo(audit.get("rgstr_dttm"));
    }

    @Test
    @DisplayName("일괄 수정 응답은 요청 순서 그대로다 — DB가 정한 순서가 아니다")
    void 일괄_수정_응답이_요청_순서다() {
        add(new CtrlGrpAddRequest("CG-A1", "A1", UseYn.Y, 1));
        add(new CtrlGrpAddRequest("CG-A2", "A2", UseYn.Y, 2));

        var body = Map.of("requests", List.of(
                Map.of("ctrlGrpId", "CG-A2", "ctrlGrpNm", "두번째가 먼저"),
                Map.of("ctrlGrpId", "CG-A1", "ctrlGrpNm", "첫번째가 나중")));

        JsonNode modified = data(rest.exchange(BASE + "/list", HttpMethod.PUT, authed(body), String.class));

        assertThat(modified.valueStream().map(n -> n.path("ctrlGrpId").asString()).toList())
                .containsExactly("CG-A2", "CG-A1");
    }

    @Test
    @DisplayName("수정으로 이름을 공백만으로 만들 수 없다 — 등록은 막는데 수정이 뚫리면 안 된다")
    void 공백_이름은_수정으로도_들어가지_못한다() {
        add(new CtrlGrpAddRequest("CG-011", "정상 이름", UseYn.Y, 1));

        ResponseEntity<String> response = rest.exchange(BASE + "/CG-011", HttpMethod.PUT,
                authed(new CtrlGrpModifyRequest("   ", null, null)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-400");
        // NOT NULL 컬럼이지만 PostgreSQL은 빈 문자열을 받으므로 DB가 걸러 주지 않는다 — 값으로 확인한다
        assertThat(data(get(BASE + "/CG-011")).path("ctrlGrpNm").asString()).isEqualTo("정상 이름");
    }

    @Test
    @DisplayName("수정 대상은 경로가 정한다 — 본문에 식별자가 없어 둘이 어긋날 수 없다")
    void 수정_대상은_경로가_정한다() {
        add(new CtrlGrpAddRequest("CG-007", "대상", UseYn.Y, 1));
        add(new CtrlGrpAddRequest("CG-008", "무관", UseYn.Y, 2));

        rest.exchange(BASE + "/CG-007", HttpMethod.PUT,
                authed(new CtrlGrpModifyRequest("바뀜", null, null)), String.class);

        assertThat(data(get(BASE + "/CG-007")).path("ctrlGrpNm").asString()).isEqualTo("바뀜");
        assertThat(data(get(BASE + "/CG-008")).path("ctrlGrpNm").asString()).isEqualTo("무관");
    }

    @Test
    @DisplayName("목록이 비어 있으면 빈 배열 200이다 — 404가 아니다")
    void 빈_목록은_오류가_아니다() {
        ResponseEntity<String> response = get(BASE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("code").asString()).isEqualTo("SUCCESS");
        assertThat(body.path("data").isArray()).isTrue();
        assertThat(body.path("data")).isEmpty();
    }

    @Test
    @DisplayName("없는 ID 단건 조회는 404다 — 지목한 자원이 없는 것은 실제 오류다")
    void 없는_단건은_404다() {
        ResponseEntity<String> response = get(BASE + "/없는ID");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-404");
    }

    @Test
    @DisplayName("정렬순서가 NULL인 행은 뒤로 가고, 동순위는 ID로 확정된다")
    void 정렬이_결정적이다() {
        add(new CtrlGrpAddRequest("CG-B", "정렬없음B", UseYn.Y, null));
        add(new CtrlGrpAddRequest("CG-A", "정렬없음A", UseYn.Y, null));
        add(new CtrlGrpAddRequest("CG-D", "동순위D", UseYn.Y, 5));
        add(new CtrlGrpAddRequest("CG-C", "동순위C", UseYn.Y, 5));

        // 두 번 조회해 같은 순서인지까지 본다 — 한 번만 보면 우연히 맞은 것과 구별되지 않는다
        assertThat(idsOf(get(BASE)))
                .containsExactly("CG-C", "CG-D", "CG-A", "CG-B")
                .isEqualTo(idsOf(get(BASE)));
    }

    @Test
    @DisplayName("사용여부로 목록을 거른다")
    void 사용여부로_거른다() {
        add(new CtrlGrpAddRequest("CG-Y", "사용", UseYn.Y, 1));
        add(new CtrlGrpAddRequest("CG-N", "미사용", UseYn.N, 2));

        assertThat(idsOf(get(BASE + "?useYn=Y"))).containsExactly("CG-Y");
        assertThat(idsOf(get(BASE + "?useYn=N"))).containsExactly("CG-N");
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401 + COMMON-401 — 게이트웨이 우회 직접 호출 방어")
    void 토큰이_없으면_401이다() {
        ResponseEntity<String> response = rest.getForEntity(BASE, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 필터 단계 401도 web-starter의 전역 예외 규약과 같은 봉투로 나가야 한다
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-401");
    }

    @Test
    @DisplayName("형식 검증 실패는 COMMON-400 + 필드별 상세다")
    void 형식_검증이_걸린다() {
        // 제어그룹명이 공백 — @NotBlank 위반. 존재 검증이 아니라 형식 검증이라는 점에 주의한다
        ResponseEntity<String> response = rest.exchange(BASE, HttpMethod.POST,
                authed(new CtrlGrpAddRequest("CG-009", "  ", UseYn.Y, 1)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("code").asString()).isEqualTo("COMMON-400");
        assertThat(body.path("data").get(0).path("field").asString()).isEqualTo("ctrlGrpNm");
        assertThat(count()).isZero();
    }

    // ── 보조 ──────────────────────────────────────────────────────

    private ResponseEntity<String> add(CtrlGrpAddRequest request) {
        ResponseEntity<String> response = rest.exchange(BASE, HttpMethod.POST, authed(request), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private ResponseEntity<String> get(String path) {
        return rest.exchange(path, HttpMethod.GET, authed(null), String.class);
    }

    private static JsonNode data(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody()).path("data");
    }

    private static List<String> idsOf(ResponseEntity<String> response) {
        return data(response).valueStream().map(node -> node.path("ctrlGrpId").asString()).toList();
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM ems.ctrl_grp_m").query(Long.class).single();
    }

    private Map<String, Object> auditColumnsOf(String ctrlGrpId) {
        return jdbc.sql("""
                        SELECT rgstr_dttm, rgstr_id, mdf_dttm, mdf_id
                          FROM ems.ctrl_grp_m
                         WHERE ctrl_grp_id = ?
                        """)
                .param(ctrlGrpId)
                .query()
                .singleRow();
    }

    /** 인증 헤더를 붙인다 — 전 엔드포인트가 authenticated()라 토큰 없이는 401이다 */
    private static HttpEntity<Object> authed(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SwtpTestJwt.accessToken(TESTER, "테스터", "ADMIN"));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(body, headers);
    }
}
