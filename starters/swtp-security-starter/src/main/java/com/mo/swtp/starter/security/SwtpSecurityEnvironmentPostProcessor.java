package com.mo.swtp.starter.security;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 보안 기본값을 최저 우선순위로 주입한다 — config-server/앱 설정이 항상 이긴다.
 *
 * <p>공개키 위치를 {@code swtp.auth.jwks-uri} 한 곳으로 모으는 것이 요점이다
 * ({@code swtp.db.host}와 같은 SSOT 패턴). 발급처가 바뀌어도 이 값 하나만 바꾸면 된다 —
 * 스프링 표준 키를 서비스마다 직접 쓰면 전환 시 11개 앱 설정을 모두 손봐야 한다.
 */
public class SwtpSecurityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "swtpSecurityDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            // 정수장별 인증 체계 차이를 흡수하는 스위치 — 실제 결정은 SWTP_AUTH_MODE 환경변수가 내린다.
            // 여기 값은 환경변수도 config-server도 없을 때의 기본값이다 (SwtpAuthMode 참고).
            // 기본이 none인 이유는 배포 현실이다 — 다수 정수장이 인증·인가를 앞단 포털/SSO에서
            // 끝내고 이 플랫폼에 넘긴다. 자체 검증(internal)은 소수라 켜는 쪽을 명시하게 한다.
            "swtp.auth.mode", "${SWTP_AUTH_MODE:none}",
            // 로컬 단독 기동 기준값 — 컨테이너 환경은 SWTP_AUTH_JWKS_URI로 mode와 함께 재정의한다
            "swtp.auth.jwks-uri", "${SWTP_AUTH_JWKS_URI:http://localhost:8085/api/auth/.well-known/jwks.json}",
            // mode=none이어도 이 파생값은 그대로 둔다 — 리소스 서버 체인을 얹지 않으므로 쓰이지 않고,
            // 값을 비우면 이 키를 참조하는 Boot 자동구성 쪽에서 플레이스홀더 해석이 깨진다
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "${swtp.auth.jwks-uri}");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
    }
}
