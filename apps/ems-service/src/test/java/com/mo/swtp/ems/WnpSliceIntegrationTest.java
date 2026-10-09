package com.mo.swtp.ems;

import java.util.List;
import java.util.Map;

import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.ems.wnp.dto.WnpAddRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyItemRequest;
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
 * 수계통지점 슬라이스 통합 검증.
 *
 * <p>제어그룹과 구조가 같으므로 매핑 왕복·감사 컬럼처럼 이미 증명된 축을 되풀이하지 않고,
 * <b>이 도메인이 독립 표면을 갖는다는 것</b>과 일괄 경로를 확인한다.
 * 두 도메인을 가른 근거가 "각자 독립 CRUD 표면"이므로, 그 전제가 실제로 성립하는지는 여기서만 보인다.
 */
@DisplayName("ems 수계통지점 슬라이스")
class WnpSliceIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BASE = "/api/ems/wnp";
    private static final String TESTER = "wnp-tester";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void 테이블을_비운다() {
        jdbc.sql("DELETE FROM ems.wnp_m").update();
    }

    @Test
    @DisplayName("제어그룹과 독립된 경로로 등록·조회된다")
    void 독립_표면을_갖는다() {
        ResponseEntity<String> created = rest.exchange(BASE, HttpMethod.POST,
                authed(new WnpAddRequest("WNP-001", "정수지 유출 분기", UseYn.Y, 1)), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(created).path("wnpNm").asString()).isEqualTo("정수지 유출 분기");

        JsonNode found = data(get(BASE + "/WNP-001"));
        assertThat(found.path("wnpId").asString()).isEqualTo("WNP-001");
        assertThat(found.path("useYn").asString()).isEqualTo("Y");
        // 감사 주체가 토큰 sub다 — 제어그룹과 다른 사용자로 발급해 값이 실제로 따라오는지 본다
        assertThat(found.path("rgstrId").asString()).isEqualTo(TESTER);
        assertThat(found.path("mdfId").asString()).isEqualTo(TESTER);
    }

    @Test
    @DisplayName("일괄 등록과 일괄 수정이 동작한다")
    void 일괄_경로가_동작한다() {
        var addBody = Map.of("requests", List.of(
                new WnpAddRequest("WNP-A", "A지점", UseYn.Y, 2),
                new WnpAddRequest("WNP-B", "B지점", UseYn.Y, 1)));

        ResponseEntity<String> added = rest.exchange(BASE + "/list", HttpMethod.POST,
                authed(addBody), String.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 정렬순서대로 나온다 — 등록 순서가 아니다
        assertThat(idsOf(get(BASE))).containsExactly("WNP-B", "WNP-A");

        var modifyBody = Map.of("requests", List.of(
                new WnpModifyItemRequest("WNP-A", "A지점(개명)", UseYn.N, null)));

        ResponseEntity<String> modified = rest.exchange(BASE + "/list", HttpMethod.PUT,
                authed(modifyBody), String.class);
        assertThat(modified.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode found = data(get(BASE + "/WNP-A"));
        assertThat(found.path("wnpNm").asString()).isEqualTo("A지점(개명)");
        assertThat(found.path("useYn").asString()).isEqualTo("N");
        // null로 보낸 필드는 그대로다
        assertThat(found.path("sortOrd").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("일괄 수정에 없는 ID가 섞이면 전부 되돌린다")
    void 일괄_수정은_전부_아니면_전무다() {
        rest.exchange(BASE, HttpMethod.POST,
                authed(new WnpAddRequest("WNP-C", "C지점", UseYn.Y, 1)), String.class);

        var body = Map.of("requests", List.of(
                new WnpModifyItemRequest("WNP-C", "바뀌면 안 됨", null, null),
                new WnpModifyItemRequest("WNP-없음", "없는 대상", null, null)));

        ResponseEntity<String> response = rest.exchange(BASE + "/list", HttpMethod.PUT,
                authed(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // 롤백을 응답 코드가 아니라 실제 값으로 확인한다 — 일부만 반영된 상태가 남으면 여기서 드러난다
        assertThat(data(get(BASE + "/WNP-C")).path("wnpNm").asString()).isEqualTo("C지점");
    }

    @Test
    @DisplayName("목록이 비어 있으면 빈 배열 200이다")
    void 빈_목록은_오류가_아니다() {
        ResponseEntity<String> response = get(BASE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        // isArray()를 먼저 단언한다 — path()는 키가 없으면 MissingNode를 돌려주고 그것도 "비어 있음"이라
        // isEmpty()만 걸면 data 필드가 통째로 사라져도 통과하는 공허 참이 된다
        assertThat(body.path("data").isArray()).isTrue();
        assertThat(body.path("data")).isEmpty();
    }

    @Test
    @DisplayName("정렬순서가 NULL인 행은 뒤로 가고, 동순위는 ID로 확정된다")
    void 정렬이_결정적이다() {
        var body = Map.of("requests", List.of(
                new WnpAddRequest("WNP-Z", "정렬없음Z", UseYn.Y, null),
                new WnpAddRequest("WNP-Y", "정렬없음Y", UseYn.Y, null),
                new WnpAddRequest("WNP-X", "동순위X", UseYn.Y, 3),
                new WnpAddRequest("WNP-W", "동순위W", UseYn.Y, 3)));
        rest.exchange(BASE + "/list", HttpMethod.POST, authed(body), String.class);

        // 두 리포지토리가 같은 정렬 규칙을 갖는다는 주장은 wnp 쪽에서도 실측돼야 한다
        assertThat(idsOf(get(BASE)))
                .containsExactly("WNP-W", "WNP-X", "WNP-Y", "WNP-Z")
                .isEqualTo(idsOf(get(BASE)));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void 토큰이_없으면_401이다() {
        assertThat(rest.getForEntity(BASE, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── 보조 ──────────────────────────────────────────────────────

    private ResponseEntity<String> get(String path) {
        return rest.exchange(path, HttpMethod.GET, authed(null), String.class);
    }

    private static JsonNode data(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody()).path("data");
    }

    private static List<String> idsOf(ResponseEntity<String> response) {
        return data(response).valueStream().map(node -> node.path("wnpId").asString()).toList();
    }

    private static HttpEntity<Object> authed(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SwtpTestJwt.accessToken(TESTER, "수계통 테스터", "ADMIN"));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(body, headers);
    }
}
