package com.mo.swtp.starter.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 영속성 공통 자동구성 — JDBC/Flyway/p6spy는 build.gradle 의존성만으로 이미 동작하므로
 * (드라이버·URL은 config-server SSOT), 이 클래스는 마커 등록만 담당한다.
 * JPA 전용 부가기능은 {@link SwtpJpaAuditingAutoConfiguration}이 조건부로 맡는다.
 */
@AutoConfiguration
public class SwtpPersistenceAutoConfiguration {

    /** 자동구성 로드 여부를 확인하기 위한 마커 빈 */
    @Bean
    public SwtpPersistenceStarterMarker swtpPersistenceStarterMarker() {
        return new SwtpPersistenceStarterMarker();
    }
}
