package com.mo.swtp.master;

import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.master.drvmd.repository.DrvmdChgRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운전모드 변경이력 조회 슬라이스 — {@code GET /api/master/drvmd}, {@code /mode}.
 *
 * <p><b>테스트가 INSERT를 하는 것은 계약 위반이 아니다.</b> 이 클래스는 ems·autonomous를 대역한다 —
 * 실제 운영에서 이력을 쓰는 것은 그 서비스들이고(02 결정 1), master 운영 코드에는 write 경로가 없다.
 * 그 부재를 {@link #GET_외의_매핑이_없다()}가 API 표면 쪽에서 단언한다.
 */
@DisplayName("운전모드 변경이력 조회")
class DrvmdChgQueryIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 조회 대상 공정 — 전환 3건이 이 대상에 쌓인다. */
    private static final String PRCS_ID = "P001";
    /** 다른 대상(제어그룹) — 대상 격리를 확인하기 위한 잡음 데이터. */
    private static final String GRP_ID = "G001";

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 8, 1, 9, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 8, 10, 14, 30, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 8, 20, 3, 15, 0);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DrvmdChgRepository drvmdChgRepository;

    /**
     * actuator가 {@code controllerEndpointHandlerMapping}이라는 두 번째
     * {@link RequestMappingHandlerMapping}을 등록하므로 타입만으로는 주입되지 않는다.
     * MVC 컨트롤러의 매핑을 보려면 이름을 지정해야 한다.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void 이력을_새로_깐다() {
        jdbc.sql("DELETE FROM operation.drvmd_chg_h").update();

        // P001: AI_ANLS → AI_RCMD → AI → AI_ANLS (마지막은 인터록 발동으로 운전원이 강등)
        insert(ControlTargetType.PRCS, PRCS_ID, "응집공정", "AI_ANLS", "AI_RCMD", "AUTO", "APRV", null, T1);
        insert(ControlTargetType.PRCS, PRCS_ID, "응집공정", "AI_RCMD", "AI", "AUTO", "APRV", null, T2);
        insert(ControlTargetType.PRCS, PRCS_ID, "응집공정", "AI", "AI_ANLS", "AUTO", "ITLCK",
                "2번 응집지 인터록 발동", T3);

        // G001: 다른 대상 — 위 조회에 섞여 나오면 안 된다
        insert(ControlTargetType.CTRL_GRP, GRP_ID, "송수펌프 1군", "AI_ANLS", "AI_RCMD", "EMS", "OPRTR",
                null, LocalDateTime.of(2026, 8, 15, 0, 0, 0));
    }

    // ── 검증 2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이력이 없는 대상은 AI_ANLS이고 initialDefault다 — 초기 이력 행 없이 성립한다(03 결정 5)")
    void 이력이_없는_대상은_초기값으로_답한다() {
        JsonNode data = modeOf("PRCS", "P999", null);

        assertThat(data.path("drvmd").asString()).isEqualTo("AI_ANLS");
        assertThat(data.path("initialDefault").asBoolean()).isTrue();
        assertThat(data.path("sinceDttm").isNull())
                .as("한 번도 안 바뀌었으므로 '그 모드가 된 시각'이 없다")
                .isTrue();
    }

    // ── 검증 3 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("at 시각의 모드는 그 시점 이하의 최신 행이다 — 사고 조사의 첫 질문")
    void at_시각의_모드를_되돌린다() {
        // T2(8/10 AI로 상향)와 T3(8/20 AI_ANLS로 강등) 사이 → 그때는 AI였다
        JsonNode between = modeOf("PRCS", PRCS_ID, "2026-08-15T00:00:00");
        assertThat(between.path("drvmd").asString()).isEqualTo("AI");
        assertThat(between.path("sinceDttm").asString()).startsWith("2026-08-10T14:30");
        assertThat(between.path("initialDefault").asBoolean()).isFalse();

        // T1 이전 → 그 시점까지는 아무 전환도 없었다
        JsonNode before = modeOf("PRCS", PRCS_ID, "2026-07-31T23:59:59");
        assertThat(before.path("drvmd").asString()).isEqualTo("AI_ANLS");
        assertThat(before.path("initialDefault").asBoolean()).isTrue();

        // 같은 대상에 이력이 3건 있는데도 위가 true다 — initialDefault 는 "기준 시각까지 없음"이지
        // "한 번도 안 바뀜"이 아니다. 필드명이 neverChanged 였을 때 이 조합이 계약을 반증했다(04 함정 7).
        assertThat(historyOf(PRCS_ID, null, null))
                .as("initialDefault=true 인 시점 조회와 무관하게 이 대상에는 전환 이력이 존재한다")
                .hasSize(3);

        // T1 정각 → 경계는 포함이다
        JsonNode atT1 = modeOf("PRCS", PRCS_ID, "2026-08-01T09:00:00");
        assertThat(atT1.path("drvmd").asString()).isEqualTo("AI_RCMD");
    }

    @Test
    @DisplayName("at을 생략하면 현재 모드이며, AI_ANLS라도 initialDefault와 구분된다")
    void 현재_모드는_at_생략형이다() {
        JsonNode now = modeOf("PRCS", PRCS_ID, null);

        // 마지막 전환(T3)이 AI로부터 AI_ANLS로 강등한 것이므로 현재 모드는 AI_ANLS다.
        assertThat(now.path("drvmd").asString()).isEqualTo("AI_ANLS");
        // 값은 초기값과 같지만 "한 번도 안 바뀜"이 아니다 — 이 구분이 DrvmdModeResponse의 존재 이유다.
        assertThat(now.path("initialDefault").asBoolean())
                .as("인터록으로 강등된 AI_ANLS와 한 번도 안 바뀐 AI_ANLS는 감사상 다른 사실이다")
                .isFalse();
        assertThat(now.path("sinceDttm").asString()).startsWith("2026-08-20T03:15");
    }

    // ── 검증 4 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이력 목록은 최신순이고 from/to 경계를 포함한다")
    void 기간_조회는_역순이고_경계를_포함한다() {
        JsonNode all = historyOf(PRCS_ID, null, null);
        assertThat(datetimes(all))
                .as("rgstr_dttm 역순")
                .containsExactly("2026-08-20T03:15", "2026-08-10T14:30", "2026-08-01T09:00");

        // from 을 T1 정각으로 주면 T1 행이 포함된다(>= 경계)
        JsonNode fromT1 = historyOf(PRCS_ID, "2026-08-01T09:00:00", null);
        assertThat(datetimes(fromT1)).hasSize(3);

        // to 를 T2 정각으로 주면 T2 행이 포함된다(<= 경계)
        JsonNode toT2 = historyOf(PRCS_ID, null, "2026-08-10T14:30:00");
        assertThat(datetimes(toT2)).containsExactly("2026-08-10T14:30", "2026-08-01T09:00");

        // 양쪽을 좁히면 가운데 1건
        JsonNode narrow = historyOf(PRCS_ID, "2026-08-02T00:00:00", "2026-08-19T00:00:00");
        assertThat(datetimes(narrow)).containsExactly("2026-08-10T14:30");
    }

    @Test
    @DisplayName("다른 제어대상의 이력은 섞이지 않는다 — 다형 참조의 판별이 실제로 동작한다")
    void 대상별로_격리된다() {
        JsonNode prcs = historyOf(PRCS_ID, null, null);
        assertThat(prcs).hasSize(3);

        // 같은 ID를 유형만 바꿔 물으면 비어야 한다 — ctrl_trgt_type_cd 가 조건에 실제로 들어간다는 뜻이다.
        ResponseEntity<String> wrongType = rest.getForEntity(
                "/api/master/drvmd?ctrlTrgtType={t}&ctrlTrgtId={id}", String.class, "CTRL_GRP", PRCS_ID);
        assertThat(JSON.readTree(wrongType.getBody()).path("data")).isEmpty();

        // 비어 있어도 404가 아니다 — "이력 없음"은 정상 상태다.
        assertThat(wrongType.getStatusCode().value()).isEqualTo(200);
    }

    // ── 검증 5 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("/api/master/drvmd 하위에 GET 외 매핑이 없다 — write 계약의 자동 그물")
    void GET_외의_매핑이_없다() {
        List<RequestMappingInfo> drvmdMappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition().getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith("/api/master/drvmd")))
                .toList();

        // 공허 참 차단 — 경로가 하나도 안 잡히면 아래 단언은 언제나 통과한다.
        assertThat(drvmdMappings)
                .as("drvmd 슬라이스가 실제로 노출한 엔드포인트")
                .hasSize(2);

        assertThat(drvmdMappings.stream()
                .filter(info -> !Set.of(RequestMethod.GET).equals(info.getMethodsCondition().getMethods()))
                .toList())
                .as("이력 생성 엔드포인트가 master에 생기면 여기서 걸린다 — INSERT는 ems·autonomous의 몫이다")
                .isEmpty();
    }

    // ── 검증 6 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("토큰 없는 직접 호출은 401 — 게이트웨이 우회를 차단한다")
    void 토큰_없는_호출은_거부된다() {
        ResponseEntity<String> response = callWithoutToken(
                "/api/master/drvmd?ctrlTrgtType=PRCS&ctrlTrgtId=" + PRCS_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── 검증 7 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("코드 체계에 없는 값은 어느 행·컬럼·값인지 알려주며 실패한다 — 조용히 넘기지 않는다")
    void 오염된_코드값은_행을_지목하며_터진다() {
        // V3에 CHECK 제약이 없어 DB는 이 값을 그대로 받는다(02 결정 5) — 그물은 리포지토리뿐이다.
        jdbc.sql("UPDATE operation.drvmd_chg_h SET af_drvmd_cd = 'AIRCMD' WHERE ctrl_trgt_id = :id")
                .param("id", PRCS_ID)
                .update();

        // 던지는 것은 IllegalStateException 이지만 @Repository 의 예외 번역기가 DataAccessException 으로
        // 감싼다. 감싸도 메시지는 보존되므로 "어느 행·컬럼·값인가"라는 요구는 그대로 충족된다 —
        // 타입까지 못박으면 번역 계층에 결합되므로 원인 체인으로 확인한다.
        assertThatThrownBy(() ->
                drvmdChgRepository.findHistory(ControlTargetType.PRCS, PRCS_ID, null, null, 100))
                .isInstanceOf(DataAccessException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("af_drvmd_cd")
                .hasMessageContaining("AIRCMD")
                .hasMessageContaining("hist_id=")
                .hasMessageContaining("AI_RCMD");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private void insert(ControlTargetType type, String targetId, String targetNm,
                        String before, String after, String issuer, String reason,
                        String remark, LocalDateTime at) {
        jdbc.sql("""
                        INSERT INTO operation.drvmd_chg_h
                            (ctrl_trgt_type_cd, ctrl_trgt_id, ctrl_trgt_nm,
                             bf_drvmd_cd, af_drvmd_cd, iss_svc_cd,
                             chg_rsn_cd, chg_rsn_rmrk, rgstr_dttm, rgstr_id)
                        VALUES (:type, :targetId, :targetNm,
                                :before, :after, :issuer,
                                :reason, :remark, :at, :rgstrId)
                        """)
                .param("type", type.name())
                .param("targetId", targetId)
                .param("targetNm", targetNm)
                .param("before", before)
                .param("after", after)
                .param("issuer", issuer)
                .param("reason", reason)
                .param("remark", remark)
                .param("at", at)
                .param("rgstrId", "it-user")
                .update();
    }

    /** {@code GET /api/master/drvmd/mode} 의 data 노드 */
    private JsonNode modeOf(String type, String targetId, String at) {
        String url = "/api/master/drvmd/mode?ctrlTrgtType={t}&ctrlTrgtId={id}"
                + (at != null ? "&at={at}" : "");
        ResponseEntity<String> response = at != null
                ? rest.getForEntity(url, String.class, type, targetId, at)
                : rest.getForEntity(url, String.class, type, targetId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JSON.readTree(response.getBody()).path("data");
    }

    /** {@code GET /api/master/drvmd} 의 data 배열 */
    private JsonNode historyOf(String targetId, String from, String to) {
        StringBuilder url = new StringBuilder("/api/master/drvmd?ctrlTrgtType=PRCS&ctrlTrgtId=" + targetId);
        if (from != null) {
            url.append("&from=").append(from);
        }
        if (to != null) {
            url.append("&to=").append(to);
        }
        ResponseEntity<String> response = rest.getForEntity(url.toString(), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JSON.readTree(response.getBody()).path("data");
    }

    /** 응답 배열의 rgstr_dttm 을 분 단위까지 잘라 순서 비교용으로 뽑는다. */
    private static List<String> datetimes(JsonNode data) {
        return data.valueStream()
                .map(node -> node.path("rgstrDttm").asString())
                .map(s -> s.substring(0, 16))
                .toList();
    }
}
