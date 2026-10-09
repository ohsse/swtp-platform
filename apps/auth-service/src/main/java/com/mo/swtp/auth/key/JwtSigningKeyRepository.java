package com.mo.swtp.auth.key;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JwtSigningKeyRepository extends JpaRepository<JwtSigningKey, String> {

    Optional<JwtSigningKey> findByActiveTrue();
}
