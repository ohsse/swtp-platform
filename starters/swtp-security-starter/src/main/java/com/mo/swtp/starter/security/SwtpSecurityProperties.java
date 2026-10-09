package com.mo.swtp.starter.security;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 공통 보안 설정 ({@code swtp.security.*}).
 *
 * <p>인증 예외 경로를 두 목록으로 나눈 이유: 하나로 두면 앱이 자기 경로 하나를 추가하려고
 * 목록을 재정의하는 순간 actuator·API 문서 경로까지 통째로 사라진다. 리스트형 프로퍼티는
 * 병합이 아니라 <b>치환</b>이기 때문이다. 그래서 플랫폼 기본값과 앱 추가분을 분리하고 합집합을 쓴다.
 */
@ConfigurationProperties("swtp.security")
public class SwtpSecurityProperties {

    /**
     * 플랫폼 기본 인증 예외 경로. 앱이 통째로 바꾸고 싶을 때만 재정의한다.
     *
     * <p>actuator를 열어두는 것은 Prometheus 스크래핑과 compose healthcheck가 토큰 없이
     * 접근해야 하기 때문이다 — 외부 노출 차단은 게이트웨이 라우팅과 네트워크 정책이 담당한다.
     */
    private List<String> permitAllPaths = new ArrayList<>(List.of(
            "/actuator/**",
            // springdoc 기본 경로와 Phase 11의 게이트웨이 정렬 경로(/api/{name}/...)를 모두 연다
            "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**",
            "/api/*/v3/api-docs/**", "/api/*/swagger-ui/**", "/api/*/swagger-ui.html"));

    /** 앱이 추가하는 인증 예외 경로 (예: auth-service의 로그인·JWKS). 기본값은 비어 있다 */
    private List<String> publicPaths = new ArrayList<>();

    public List<String> getPermitAllPaths() {
        return permitAllPaths;
    }

    public void setPermitAllPaths(List<String> permitAllPaths) {
        this.permitAllPaths = permitAllPaths;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    /** SecurityFilterChain에 넘길 최종 예외 경로 (기본값 ∪ 앱 추가분) */
    public String[] resolvedPermitAllPaths() {
        return Stream.concat(permitAllPaths.stream(), publicPaths.stream())
                .distinct()
                .toArray(String[]::new);
    }
}
