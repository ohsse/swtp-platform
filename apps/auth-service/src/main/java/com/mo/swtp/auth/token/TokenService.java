package com.mo.swtp.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.mo.swtp.auth.config.AuthProperties;
import com.mo.swtp.auth.key.JwtKeyProvider;
import com.mo.swtp.auth.user.User;
import com.mo.swtp.auth.user.UserRepository;
import com.mo.swtp.auth.user.UserRoleRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.common.security.SwtpJwtClaims;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 발급·회전·폐기 — 플랫폼에서 서명 개인키를 만지는 유일한 지점.
 *
 * <p>액세스 토큰과 리프레시 토큰의 검증 방식이 다르다는 점이 설계의 핵심이다.
 * <ul>
 *   <li>액세스 토큰: 서명만으로 검증 → 상태 조회가 없어 빠르지만 <b>만료 전 폐기가 불가능</b>하다.
 *       그래서 유효기간을 짧게 잡는다</li>
 *   <li>리프레시 토큰: DB 조회로 검증 → 언제든 폐기 가능하므로 길게 잡는다.
 *       대신 매 사용 시 <b>회전</b>(기존 폐기 + 신규 발급)해 탈취 창을 좁힌다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    /** 256비트 — 추측 공격이 성립하지 않는 수준. Base64url 인코딩해 전달한다 */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtKeyProvider keyProvider;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBreachService breachService;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedTokens issue(User user) {
        return issueFor(user);
    }

    /**
     * 리프레시 토큰 회전.
     *
     * <p>이미 폐기된 토큰이 다시 제출되면 <b>탈취 정황</b>으로 본다 — 정상 클라이언트는 회전 후
     * 옛 토큰을 다시 쓰지 않기 때문이다. 이때 해당 사용자의 모든 리프레시 토큰을 끊어
     * 공격자와 정상 사용자를 함께 로그아웃시킨다(사용자는 재로그인하면 되지만, 공격자는 자격이 없다).
     */
    @Transactional
    public IssuedTokens rotate(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(TokenService::invalidRefreshToken);

        if (stored.isRevoked()) {
            log.warn("폐기된 리프레시 토큰 재사용 감지 — 사용자 {}의 전체 토큰을 폐기한다", stored.getUserId());
            // 아래 throw로 이 트랜잭션은 롤백된다. 대응이 함께 취소되지 않도록 별도 트랜잭션에서 커밋한다.
            breachService.revokeAllOf(stored.getUserId());
            throw invalidRefreshToken();
        }
        if (!stored.isUsable(now)) {
            throw invalidRefreshToken();
        }

        stored.revoke(now);
        User user = userRepository.findById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(TokenService::invalidRefreshToken);
        return issueFor(user);
    }

    /** 로그아웃 — 존재하지 않는 토큰이어도 조용히 성공시킨다(토큰 존재 여부를 알려주지 않는다) */
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    /**
     * WebSocket/SSE 핸드셰이크용 단기 티켓.
     *
     * <p>브라우저의 {@code EventSource}/{@code WebSocket}은 Authorization 헤더를 붙일 수 없어
     * 자격을 쿼리 파라미터로 넘겨야 한다. 액세스 토큰을 그대로 넘기면 접근 로그·Referer에
     * 장수명 토큰이 남으므로, 수십 초짜리 별도 토큰을 발급하고 {@code typ}으로 용도를 구분한다.
     */
    public String issueWsTicket(String userId, String username, List<String> roles) {
        return sign(userId, username, roles, SwtpJwtClaims.TOKEN_TYPE_WS_TICKET,
                properties.getWsTicketValidity());
    }

    private IssuedTokens issueFor(User user) {
        List<String> roles = userRoleRepository.findRoleIdsByUserId(user.getUserId());
        String accessToken = sign(user.getUserId(), user.getUsername(), roles,
                SwtpJwtClaims.TOKEN_TYPE_ACCESS, properties.getAccessTokenValidity());

        String rawRefreshToken = randomRefreshToken();
        refreshTokenRepository.save(new RefreshToken(hash(rawRefreshToken), user.getUserId(),
                LocalDateTime.now().plus(properties.getRefreshTokenValidity())));

        return new IssuedTokens(accessToken, rawRefreshToken,
                properties.getAccessTokenValidity().toSeconds(), roles);
    }

    private String sign(String userId, String username, List<String> roles,
            String tokenType, java.time.Duration validity) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(validity))
                .id(UUID.randomUUID().toString())
                .claim(SwtpJwtClaims.USERNAME, username)
                .claim(SwtpJwtClaims.ROLES, roles)
                .claim(SwtpJwtClaims.TOKEN_TYPE, tokenType)
                .build();
        // kid를 명시해야 검증 측이 JWKS의 여러 공개키 중 올바른 것을 고를 수 있다 (키 회전 대비)
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyProvider.activeKey().getKeyID())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 저장·조회 모두 이 해시로만 이뤄진다 — 원문은 응답 이후 서버에 남지 않는다 */
    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (Exception ex) {
            throw new IllegalStateException("리프레시 토큰 해시 실패", ex);
        }
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(CommonErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다");
    }
}
