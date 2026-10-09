package com.mo.swtp.starter.security.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mo.swtp.common.security.SwtpJwtClaims;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 통합 테스트용 JWT 발급기 — 각 서비스가 auth-service 없이 인증 시나리오를 검증할 수 있게 한다.
 *
 * <p>테스트 픽스처로 배포하는 이유: 토큰 발급 코드를 서비스마다 복사하면 클레임 규약이 갈라져
 * "테스트는 통과하는데 실제 토큰으로는 실패"하는 상황이 생긴다. 발급 로직을 한 곳에 두고
 * {@link com.mo.swtp.common.security.SwtpJwtClaims}를 공유해 그 갈라짐을 막는다.
 *
 * <p>키쌍은 JVM 기동 시 1회 생성한다 — 리포지토리에 개인키를 커밋하지 않기 위해서다.
 * 대신 같은 JVM 안에서는 안정적이라 {@link SwtpTestSecurityConfiguration}의 디코더와 짝이 맞는다.
 */
public final class SwtpTestJwt {

    /** 정상 경로에서 쓰는 키쌍 — 디코더도 이 키를 신뢰한다 */
    private static final RSAKey SIGNING_KEY = generateKey();

    /** 서명 불일치 시나리오 전용 — 디코더가 신뢰하지 않는 키 */
    private static final RSAKey FOREIGN_KEY = generateKey();

    private static final String ISSUER = "swtp-test";

    private SwtpTestJwt() {
    }

    /** 유효기간 10분짜리 액세스 토큰 */
    public static String accessToken(String userId, String username, String... roles) {
        return sign(SIGNING_KEY, claims(userId, username, List.of(roles),
                SwtpJwtClaims.TOKEN_TYPE_ACCESS, Duration.ofMinutes(10)));
    }

    /** 이미 만료된 토큰 — 401 검증용 */
    public static String expiredToken(String userId) {
        return sign(SIGNING_KEY, claims(userId, userId, List.of(),
                SwtpJwtClaims.TOKEN_TYPE_ACCESS, Duration.ofMinutes(-10)));
    }

    /** 신뢰하지 않는 키로 서명한 토큰 — 서명 검증 실패(401) 시나리오용 */
    public static String forgedToken(String userId) {
        return sign(FOREIGN_KEY, claims(userId, userId, List.of("ADMIN"),
                SwtpJwtClaims.TOKEN_TYPE_ACCESS, Duration.ofMinutes(10)));
    }

    /** {@link SwtpTestSecurityConfiguration}이 등록하는 디코더 — 공개키 검증만 수행한다 */
    public static JwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
        catch (Exception ex) {
            throw new IllegalStateException("테스트 디코더 생성 실패", ex);
        }
    }

    private static JwtClaimsSet claims(String userId, String username, List<String> roles,
            String tokenType, Duration validity) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId)
                .issuedAt(now)
                // 음수 validity면 이미 지난 시각이 되어 만료 토큰이 만들어진다
                .expiresAt(now.plus(validity))
                .id(UUID.randomUUID().toString())
                .claim(SwtpJwtClaims.USERNAME, username)
                .claim(SwtpJwtClaims.ROLES, roles)
                .claim(SwtpJwtClaims.TOKEN_TYPE, tokenType)
                .build();
    }

    private static String sign(RSAKey key, JwtClaimsSet claims) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        }
        catch (Exception ex) {
            throw new IllegalStateException("테스트 RSA 키쌍 생성 실패", ex);
        }
    }
}
