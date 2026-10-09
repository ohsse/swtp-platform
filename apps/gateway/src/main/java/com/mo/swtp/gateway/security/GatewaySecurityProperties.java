package com.mo.swtp.gateway.security;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게이트웨이 인증 필터 설정 ({@code swtp.gateway.security.*}).
 */
@Getter
@Setter
@ConfigurationProperties("swtp.gateway.security")
public class GatewaySecurityProperties {

    /** 인증 없이 통과시킬 경로 패턴 (Ant/PathPattern 문법) */
    private List<String> permitAllPaths = new ArrayList<>();

    /**
     * 쿼리 파라미터 티켓({@code ?ticket=})으로 인증할 수 있는 경로.
     *
     * <p>브라우저의 {@code EventSource}/{@code WebSocket}이 Authorization 헤더를 붙일 수 없어
     * 열어두는 예외다. 아무 경로에나 허용하면 티켓이 일반 API 자격으로 쓰일 수 있으므로,
     * 실시간 스트림 경로로만 좁힌다.
     */
    private List<String> ticketAuthPaths = new ArrayList<>();
}
