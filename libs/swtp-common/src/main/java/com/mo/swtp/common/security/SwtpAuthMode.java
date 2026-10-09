package com.mo.swtp.common.security;

/**
 * 인증 검증 모드 ({@code swtp.auth.mode}) — 정수장마다 인증 체계가 다른 현실을 설정 한 줄로 흡수한다.
 *
 * <p>아키텍처 4.1은 auth-service를 Optional로 두고 "정수장에 기존 인증 시스템이 존재할 경우
 * 배포하지 않을 수 있다"고 정한다. 실제 배포 현실은 <b>토큰을 검증하는 정수장과 하지 않는 정수장
 * 둘</b>이므로 두 값만 둔다. 결정은 {@code SWTP_AUTH_MODE} 환경변수가 내린다 —
 * {@code mode}와 {@code jwks-uri}는 한 쌍으로만 의미가 있으므로 함께 지정한다.
 *
 * <p><b>외부 IdP(구 {@code external}) 값을 두지 않는 이유</b>: 검증 경로가 자체 발급 토큰 규약에
 * 결합돼 있다. 게이트웨이가 {@link SwtpJwtClaims#TOKEN_TYPE} 클레임({@code typ})을 요구하는데
 * 이는 액세스 토큰과 WebSocket 티켓을 구분하려고 우리가 넣는 커스텀 클레임이라 표준 IdP 토큰에는
 * 없다. 즉 값만 늘려서는 외부 IdP가 동작하지 않으므로, 쓰지 않는 선택지를 남겨 "설정은 있는데
 * 전부 401"인 상태를 만들지 않는다. 외부 IdP 연동이 실제로 필요해지면 그때 클레임 규약부터 함께 설계한다.
 *
 * <p>이 enum이 {@code libs/swtp-common}에 있는 이유는 {@link SwtpJwtClaims}와 같다:
 * 게이트웨이(WebFlux)와 각 서비스(서블릿)는 보안 스타터를 공유할 수 없는데, 두 곳이 <b>같은 값을
 * 같은 뜻으로</b> 읽어야 한다. 한쪽만 {@code none}으로 해석하면 게이트웨이는 통과시키고 서비스는
 * 401을 내는 "설정은 맞는데 호출이 안 되는" 상태가 된다.
 */
public enum SwtpAuthMode {

    /** auth-service가 발급한 토큰을 검증한다 — 켜는 쪽을 명시해야 한다 */
    INTERNAL,

    /**
     * 검증하지 않고 통과시킨다 — <b>기본값</b>. 인증·인가를 앞단 포털/SSO가 끝내는 정수장과
     * 인증 체계가 없는 폐쇄망·데모 환경이 대상이다.
     *
     * <p>이 모드에서 감사 컬럼({@code rgstr_id}/{@code mdf_id})은 전부 시스템 기본값이 된다 —
     * 사용자를 식별할 근거가 애초에 없다. 또한 {@code @PreAuthorize} 같은 메서드 단위 권한은
     * 인증 주체가 없으므로 통과하지 못한다(403). 권한 규칙이 붙은 API가 생기면 이 모드는
     * 더 이상 성립하지 않는다.
     */
    NONE;

    /** 토큰을 검증하는 모드인가 — {@link #NONE}만 {@code false}다 */
    public boolean verifies() {
        return this != NONE;
    }
}
