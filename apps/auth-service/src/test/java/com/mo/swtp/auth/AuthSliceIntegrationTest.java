package com.mo.swtp.auth;

import java.util.Map;

import com.mo.swtp.auth.api.AuthDtos.LoginRequest;
import com.mo.swtp.auth.api.AuthDtos.RefreshTokenRequest;

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
 * Phase 10 인증 슬라이스 통합 검증 — 발급 / 검증 / 회전 / 폐기 / 감사 컬럼.
 */
class AuthSliceIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADMIN_ID = "admin";
    private static final String ADMIN_PASSWORD = "admin123!";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("로그인 → 액세스/리프레시 발급, 액세스 토큰으로 /me 조회")
    void loginIssuesTokensAndAuthenticatesMe() {
        JsonNode tokens = login();
        assertThat(tokens.path("accessToken").asString()).isNotBlank();
        assertThat(tokens.path("refreshToken").asString()).isNotBlank();
        assertThat(tokens.path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.path("roles").get(0).asString()).isEqualTo("ADMIN");

        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                bearer(tokens.path("accessToken").asString()), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JSON.readTree(me.getBody()).path("data");
        assertThat(data.path("userId").asString()).isEqualTo(ADMIN_ID);
        assertThat(data.path("displayName").asString()).isEqualTo("시스템 관리자");
    }

    @Test
    @DisplayName("토큰 없이 인증 경로 호출 → 401 + COMMON-401 (게이트웨이 우회 직접 호출 방어)")
    void missingTokenIsRejectedWithCommonEnvelope() {
        ResponseEntity<String> response = rest.getForEntity("/api/auth/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 필터 단계 401도 web-starter의 전역 예외 규약과 같은 봉투로 나가야 한다
        assertThat(JSON.readTree(response.getBody()).path("code").asString()).isEqualTo("COMMON-401");
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰 → 401")
    void tamperedTokenIsRejected() {
        // 다른 토큰의 서명을 갖다 붙인다 — 헤더/페이로드는 정상이므로 서명 검증만을 콕 집어 시험한다.
        // (마지막 글자 한 개를 바꾸는 방식은 base64url 끝자리의 잉여 비트 때문에 디코딩 결과가
        //  그대로일 수 있어 검증이 통과해 버린다)
        String[] first = login().path("accessToken").asString().split("\\.");
        String[] second = login().path("accessToken").asString().split("\\.");
        String spliced = first[0] + "." + first[1] + "." + second[2];

        ResponseEntity<String> response = rest.exchange("/api/auth/me", HttpMethod.GET,
                bearer(spliced), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401, 계정 존재 여부를 알려주지 않는다")
    void wrongCredentialsAreRejected() {
        ResponseEntity<String> wrongPassword = rest.postForEntity("/api/auth/login",
                json(new LoginRequest(ADMIN_ID, "틀린비밀번호")), String.class);
        ResponseEntity<String> unknownUser = rest.postForEntity("/api/auth/login",
                json(new LoginRequest("존재하지않는계정", ADMIN_PASSWORD)), String.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 두 실패의 응답이 구분 불가능해야 사용자 열거가 성립하지 않는다
        assertThat(JSON.readTree(unknownUser.getBody())).isEqualTo(JSON.readTree(wrongPassword.getBody()));
    }

    @Test
    @DisplayName("JWKS는 인증 없이 공개키만 노출한다 — 개인키 필드가 새지 않는다")
    void jwksExposesPublicKeyOnly() {
        ResponseEntity<String> response = rest.getForEntity("/api/auth/.well-known/jwks.json", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode key = JSON.readTree(response.getBody()).path("keys").get(0);
        assertThat(key.path("kty").asString()).isEqualTo("RSA");
        assertThat(key.path("kid").asString()).isNotBlank();
        // RSA 개인키 성분(d, p, q ...)이 하나라도 있으면 치명적 유출이다
        assertThat(key.has("d")).isFalse();
        assertThat(key.has("p")).isFalse();
    }

    @Test
    @DisplayName("리프레시는 회전한다 — 옛 토큰 재사용은 탈취로 보고 사용자 전체 토큰을 폐기한다")
    void refreshRotatesAndDetectsReuse() {
        String firstRefresh = login().path("refreshToken").asString();

        JsonNode rotated = post("/api/auth/refresh", new RefreshTokenRequest(firstRefresh), HttpStatus.OK);
        String secondRefresh = rotated.path("refreshToken").asString();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // 폐기된 옛 토큰 재제출 → 401
        ResponseEntity<String> reuse = rest.postForEntity("/api/auth/refresh",
                json(new RefreshTokenRequest(firstRefresh)), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 재사용 탐지의 대응: 방금 발급된 정상 토큰까지 함께 끊긴다
        ResponseEntity<String> afterBreach = rest.postForEntity("/api/auth/refresh",
                json(new RefreshTokenRequest(secondRefresh)), String.class);
        assertThat(afterBreach.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그아웃하면 해당 리프레시 토큰은 더 이상 쓸 수 없다")
    void logoutRevokesRefreshToken() {
        String refreshToken = login().path("refreshToken").asString();

        ResponseEntity<String> logout = rest.postForEntity("/api/auth/logout",
                json(new RefreshTokenRequest(refreshToken)), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> afterLogout = rest.postForEntity("/api/auth/refresh",
                json(new RefreshTokenRequest(refreshToken)), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("WebSocket 티켓은 인증된 사용자만 받을 수 있고 typ으로 액세스 토큰과 구분된다")
    void wsTicketRequiresAuthenticationAndIsTypeTagged() {
        assertThat(rest.postForEntity("/api/auth/ws-ticket", HttpEntity.EMPTY, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String accessToken = login().path("accessToken").asString();
        ResponseEntity<String> response = rest.exchange("/api/auth/ws-ticket", HttpMethod.POST,
                bearer(accessToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JSON.readTree(response.getBody()).path("data");
        assertThat(data.path("expiresIn").asLong()).isEqualTo(30);
        assertThat(payloadOf(data.path("ticket").asString()).path("typ").asString()).isEqualTo("ws-ticket");
        assertThat(payloadOf(accessToken).path("typ").asString()).isEqualTo("access");
    }

    @Test
    @DisplayName("감사 컬럼 규약 — 등록 시 mdf_*가 rgstr_*와 같게 채워지고, 폐기(수정) 시점에 갱신된다")
    void auditColumnsFollowTheConvention() {
        String refreshToken = login().path("refreshToken").asString();

        Map<String, Object> onInsert = auditColumnsOfLatestRefreshToken();
        assertThat(onInsert.get("rgstr_dttm")).isNotNull();
        // 인증 없이 접근하는 로그인 경로라 감사 주체는 시스템 기본값이다
        assertThat(onInsert.get("rgstr_id")).isEqualTo("SYSTEM");
        // 수정 컬럼은 NOT NULL — 등록 시점에 등록값과 동일하게 함께 채워진다.
        // "한 번도 수정되지 않음"은 이 동등성으로 판정한다.
        assertThat(onInsert.get("mdf_dttm")).isEqualTo(onInsert.get("rgstr_dttm"));
        assertThat(onInsert.get("mdf_id")).isEqualTo(onInsert.get("rgstr_id"));

        rest.postForEntity("/api/auth/logout", json(new RefreshTokenRequest(refreshToken)), String.class);

        Map<String, Object> onUpdate = auditColumnsOfLatestRefreshToken();
        // 폐기(수정) 후에는 등록 시각보다 뒤여야 한다 — 더 이상 등록값과 같지 않다
        assertThat(onUpdate.get("mdf_dttm")).isNotEqualTo(onInsert.get("mdf_dttm"));
        assertThat(onUpdate.get("mdf_id")).isEqualTo("SYSTEM");
        // 등록 컬럼은 updatable=false라 수정 시에도 그대로여야 한다
        assertThat(onUpdate.get("rgstr_dttm")).isEqualTo(onInsert.get("rgstr_dttm"));
    }

    private Map<String, Object> auditColumnsOfLatestRefreshToken() {
        return jdbcClient.sql("""
                        SELECT rgstr_dttm, rgstr_id, mdf_dttm, mdf_id
                          FROM auth.refresh_tokens
                         ORDER BY refresh_token_id DESC
                         LIMIT 1
                        """)
                .query()
                .singleRow();
    }

    private JsonNode login() {
        return post("/api/auth/login", new LoginRequest(ADMIN_ID, ADMIN_PASSWORD), HttpStatus.OK);
    }

    private JsonNode post(String path, Object body, HttpStatus expected) {
        ResponseEntity<String> response = rest.postForEntity(path, json(body), String.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        return JSON.readTree(response.getBody()).path("data");
    }

    private static HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    /** 서명 검증 없이 페이로드만 들여다본다 — 클레임 규약 확인 용도 */
    private static JsonNode payloadOf(String jwt) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        return JSON.readTree(payload);
    }
}
