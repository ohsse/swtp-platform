package com.mo.swtp.auth.key;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 활성 서명키를 기동 시 1회 확정해 보유한다 — 발급(서명)과 JWKS 공개의 단일 출처.
 *
 * <p>키가 없으면 만들어 저장한다. 인스턴스 여러 개가 동시에 최초 기동해도
 * {@code ux_jwt_signing_keys_active} 부분 유니크 인덱스 덕분에 한쪽만 성공하고,
 * 실패한 쪽은 이긴 쪽의 키를 다시 읽는다 — 서로 다른 키로 서명하는 사태를 DB가 막는다.
 *
 * <p><b>키 회전</b>은 현재 재기동이 필요하다(활성 키를 필드에 고정). 무중단 회전을 하려면
 * JWKS에 신·구 공개키를 함께 싣고 서명키만 교체하는 단계가 필요하며, 그때 이 클래스를 확장한다.
 */
@Slf4j
@Component
public class JwtKeyProvider {

    private static final String ALGORITHM = "RSA";
    private static final String JWS_ALGORITHM = "RS256";
    private static final int KEY_SIZE = 2048;

    private final RSAKey activeKey;

    public JwtKeyProvider(JwtSigningKeyRepository repository) {
        this.activeKey = toRsaKey(loadOrCreateActive(repository));
        log.info("JWT 서명키 활성화 — kid={}", this.activeKey.getKeyID());
    }

    /** 토큰 서명용 (개인키 포함) */
    public RSAKey activeKey() {
        return activeKey;
    }

    /** JWKS 엔드포인트로 공개할 집합 — 개인키는 절대 포함하지 않는다 */
    public JWKSet publicJwkSet() {
        return new JWKSet(activeKey.toPublicJWK());
    }

    private static JwtSigningKey loadOrCreateActive(JwtSigningKeyRepository repository) {
        return repository.findByActiveTrue().orElseGet(() -> {
            try {
                return repository.save(generate());
            }
            catch (DataIntegrityViolationException ex) {
                // 다른 인스턴스가 먼저 활성 키를 만들었다 — 그 키를 따른다
                log.info("활성 서명키 동시 생성 감지 — 기존 키를 사용한다");
                return repository.findByActiveTrue()
                        .orElseThrow(() -> new IllegalStateException("활성 서명키를 확정하지 못했다", ex));
            }
        });
    }

    private static JwtSigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getEncoder();
            return new JwtSigningKey(UUID.randomUUID().toString(),
                    encoder.encodeToString(keyPair.getPublic().getEncoded()),
                    encoder.encodeToString(keyPair.getPrivate().getEncoded()),
                    JWS_ALGORITHM);
        }
        catch (Exception ex) {
            throw new IllegalStateException("JWT 서명 키쌍 생성 실패", ex);
        }
    }

    private static RSAKey toRsaKey(JwtSigningKey entity) {
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            var publicKey = (RSAPublicKey) keyFactory
                    .generatePublic(new X509EncodedKeySpec(decoder.decode(entity.getPublicKey())));
            var privateKey = (RSAPrivateKey) keyFactory
                    .generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(entity.getPrivateKey())));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(entity.getKid())
                    .build();
        }
        catch (Exception ex) {
            throw new IllegalStateException("저장된 서명키 복원 실패 — kid=" + entity.getKid(), ex);
        }
    }
}
