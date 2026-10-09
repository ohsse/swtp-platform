package com.mo.swtp.auth.api;

import java.util.List;

import com.mo.swtp.auth.token.IssuedTokens;
import com.mo.swtp.starter.security.SwtpPrincipal;

import jakarta.validation.constraints.NotBlank;

/** 인증 API 요청/응답 DTO 모음 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 로그인 요청 */
    public record LoginRequest(
            @NotBlank(message = "username은 필수입니다") String username,
            @NotBlank(message = "password는 필수입니다") String password) {
    }

    /** 재발급/로그아웃 요청 */
    public record RefreshTokenRequest(
            @NotBlank(message = "refreshToken은 필수입니다") String refreshToken) {
    }

    /**
     * 토큰 응답.
     *
     * <p>{@code tokenType}을 함께 내려 클라이언트가 {@code Authorization: Bearer ...} 형식을
     * 하드코딩하지 않게 한다.
     */
    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
            long expiresIn, List<String> roles) {

        static TokenResponse from(IssuedTokens tokens) {
            return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
                    tokens.expiresInSeconds(), tokens.roles());
        }
    }

    /** 현재 로그인 사용자 */
    public record MeResponse(String userId, String username, String displayName, List<String> roles) {
    }

    /** WebSocket/SSE 핸드셰이크 티켓 */
    public record WsTicketResponse(String ticket, long expiresIn) {
    }

    /** 인증 주체가 비어 있으면 안 되는 경로에서 쓰는 방어 조회 */
    static SwtpPrincipal required(SwtpPrincipal principal) {
        if (principal == null) {
            throw new IllegalStateException("인증이 필요한 경로인데 주체가 비어 있다 — SecurityFilterChain 설정을 확인하라");
        }
        return principal;
    }
}
