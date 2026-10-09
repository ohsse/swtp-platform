package com.mo.swtp.auth.api;

import java.util.Map;

import com.mo.swtp.auth.api.AuthDtos.LoginRequest;
import com.mo.swtp.auth.api.AuthDtos.MeResponse;
import com.mo.swtp.auth.api.AuthDtos.RefreshTokenRequest;
import com.mo.swtp.auth.api.AuthDtos.TokenResponse;
import com.mo.swtp.auth.api.AuthDtos.WsTicketResponse;
import com.mo.swtp.auth.config.AuthProperties;
import com.mo.swtp.auth.key.JwtKeyProvider;
import com.mo.swtp.auth.token.TokenService;
import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.starter.security.CurrentUser;
import com.mo.swtp.starter.security.SwtpPrincipal;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 *
 * <p>게이트웨이 라우트가 {@code Path=/api/auth/**}이고 StripPrefix가 없으므로,
 * 서비스도 같은 prefix로 노출해야 게이트웨이 경유 접근이 성립한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtKeyProvider keyProvider;
    private final AuthProperties properties;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(TokenResponse.from(authService.login(request.username(), request.password())));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(TokenResponse.from(tokenService.rotate(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(request.refreshToken());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@CurrentUser SwtpPrincipal principal) {
        SwtpPrincipal current = AuthDtos.required(principal);
        var user = authService.findById(current.userId());
        return ApiResponse.ok(new MeResponse(user.getUserId(), user.getUsername(),
                user.getDisplayName(), current.roles()));
    }

    /** WebSocket/SSE 핸드셰이크용 단기 티켓 — 액세스 토큰을 쿼리 파라미터로 흘리지 않기 위한 우회로 */
    @PostMapping("/ws-ticket")
    public ApiResponse<WsTicketResponse> wsTicket(@CurrentUser SwtpPrincipal principal) {
        SwtpPrincipal current = AuthDtos.required(principal);
        String ticket = tokenService.issueWsTicket(current.userId(), current.username(), current.roles());
        return ApiResponse.ok(new WsTicketResponse(ticket, properties.getWsTicketValidity().toSeconds()));
    }

    /**
     * JWKS 공개 (RFC 7517).
     *
     * <p>여기만 {@link ApiResponse} 봉투를 쓰지 않는다 — Spring Security와 외부 IdP 클라이언트가
     * 표준 포맷을 그대로 파싱하기 때문에 봉투로 감싸면 검증 자체가 성립하지 않는다.
     */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keyProvider.publicJwkSet().toJSONObject();
    }
}
