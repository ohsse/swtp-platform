package com.mo.swtp.starter.persistence;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 영속성 기본값을 최저 우선순위로 주입한다 — config-server/앱 설정이 항상 이긴다.
 *
 * <ul>
 *   <li>DDL은 Flyway 단독 소유(ddl-auto=none) — config-server 미가동 시에도 안전한 기본값
 *       (config-repo/application.yml과 같은 값. addLast라 원격 설정이 로드되면 그쪽이 이긴다)</li>
 *   <li>HikariCP 풀 이름을 서비스명으로 고정 — 여러 서비스 로그가 섞였을 때 풀 식별 용이</li>
 * </ul>
 */
public class SwtpPersistenceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "swtpPersistenceDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "spring.jpa.hibernate.ddl-auto", "none",
            "spring.jpa.open-in-view", "false",
            "spring.datasource.hikari.maximum-pool-size", "10",
            "spring.datasource.hikari.pool-name", "${spring.application.name}-pool");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
    }
}
