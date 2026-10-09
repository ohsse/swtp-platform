package com.mo.swtp.auth.key;

import com.mo.swtp.starter.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JWT RS256 서명 키쌍.
 *
 * <p>키를 DB에 두는 이유: 재기동해도 이미 발급한 토큰이 살아 있어야 하고, auth-service를
 * 여러 개 띄워도 같은 키로 서명해야 한다. Redis를 쓰지 않는 원칙(15) 아래 공유 상태를 둘 곳은
 * PostgreSQL뿐이다(원칙 16). 파일 마운트 방식은 정수장별 배포마다 키 배포 절차가 따로 필요하다.
 *
 * <p><b>알려진 한계</b>: 개인키를 평문으로 보관한다. DB 읽기 권한이 곧 토큰 위조 권한이 되므로,
 * 운영 반입 시에는 KMS/시크릿 매니저로 옮기고 이 테이블은 공개키·kid만 남기는 것이 맞다.
 */
@Entity
@Table(name = "jwt_signing_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JwtSigningKey extends BaseEntity {

    /** JWK Key ID — 토큰 헤더에 실려 검증 측이 여러 공개키 중 하나를 고르는 근거가 된다 */
    @Id
    @Column(length = 50)
    private String kid;

    /** Base64(X.509 SubjectPublicKeyInfo) */
    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    /** Base64(PKCS#8) */
    @Column(name = "private_key", nullable = false, columnDefinition = "text")
    private String privateKey;

    @Column(nullable = false, length = 20)
    private String algorithm;

    /** 활성 키는 DB 부분 유니크 인덱스로 최대 1개가 보장된다 */
    @Column(nullable = false)
    private boolean active;

    public JwtSigningKey(String kid, String publicKey, String privateKey, String algorithm) {
        this.kid = kid;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.algorithm = algorithm;
        this.active = true;
    }
}
