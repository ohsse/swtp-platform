package com.mo.swtp.starter.persistence;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpPersistenceEnvironmentPostProcessorTest {

    private final SwtpPersistenceEnvironmentPostProcessor processor =
            new SwtpPersistenceEnvironmentPostProcessor();

    @Test
    void ddl_auto와_hikari_기본값을_제공한다() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("10");
    }

    @Test
    void 풀_이름은_서비스명으로_치환되고_앱_설정이_기본값보다_우선한다() {
        StandardEnvironment environment = new StandardEnvironment();
        // 앱 설정(config-server 상당)이 먼저 등록되어 더 높은 우선순위를 가진다
        environment.getPropertySources().addFirst(new MapPropertySource("appConfig", Map.of(
                "spring.application.name", "master-service",
                "spring.jpa.hibernate.ddl-auto", "validate")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                .isEqualTo("master-service-pool");
        // 앱 설정이 스타터 기본값(none)을 이긴다
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void addLast로_주입되어_최저_우선순위를_가진다() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().stream().toList().getLast().getName())
                .isEqualTo(SwtpPersistenceEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }
}
