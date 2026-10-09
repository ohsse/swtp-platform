package com.mo.swtp.starter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class SwtpObservabilityEnvironmentPostProcessorTest {

    private final SwtpObservabilityEnvironmentPostProcessor processor =
            new SwtpObservabilityEnvironmentPostProcessor();

    @Test
    void prometheus_엔드포인트_노출_기본값을_제공한다() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .contains("prometheus");
    }

    @Test
    void 콘솔_로그는_평문이_기본이다() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.config"))
                .isEqualTo("classpath:swtp-logback-plain.xml");
        // 포맷 선택은 logging.config가 담당한다 — 이 프로퍼티로는 평문 전환이 불가능하기 때문이다
        // (structured-console-appender.xml이 StructuredLogEncoder를 하드코딩한다)
        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
        // 파일은 어느 포맷에서든 평문 — 사람이 직접 열어 보는 감사·장애분석용 백업이다
        assertThat(environment.getProperty("logging.structured.format.file")).isNull();
    }

    @Test
    void SWTP_LOG_FORMAT이_콘솔_포맷을_고른다() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("deployEnv", Map.of("SWTP_LOG_FORMAT", "ecs")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.config"))
                .isEqualTo("classpath:swtp-logback-ecs.xml");
    }

    @Test
    void 공통_태그는_서비스명으로_치환되고_앱_설정이_기본값보다_우선한다() {
        StandardEnvironment environment = new StandardEnvironment();
        // 앱 설정(application.yml 상당)이 먼저 등록되어 더 높은 우선순위를 가진다
        environment.getPropertySources().addFirst(new MapPropertySource("appConfig", Map.of(
                "spring.application.name", "master-service",
                "management.endpoints.web.exposure.include", "health")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.metrics.tags.application"))
                .isEqualTo("master-service");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
    }
}
