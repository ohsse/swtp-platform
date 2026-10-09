package com.mo.swtp.starter.observability;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 관측성 기본값을 최저 우선순위로 주입한다 — 각 앱의 application.yml/환경변수가 항상 이긴다.
 *
 * <ul>
 *   <li>prometheus 엔드포인트 노출: /actuator/prometheus(OpenMetrics 표준) 활성화 — 수집기 무관</li>
 *   <li>공통 태그 application=서비스명: 대시보드의 서비스 단위 필터링 기준</li>
 *   <li>공통 로깅 설정: 콘솔(평문 또는 ECS JSON) + 일반/에러 파일 롤링(항상 평문)</li>
 * </ul>
 *
 * <p>콘솔 포맷은 {@code SWTP_LOG_FORMAT} 환경변수가 정한다 — {@code plain}(기본) 또는 {@code ecs}.
 * 값이 logback 파일 이름의 일부가 되어 어느 설정을 읽을지를 고른다. 포맷 전환에
 * {@code logging.structured.format.console}을 쓰지 않는 이유는, Boot 조각
 * {@code structured-console-appender.xml}이 StructuredLogEncoder를 하드코딩하고 있어
 * 그 프로퍼티를 비워도 평문이 되지 않고 오히려 기동이 깨지기 때문이다. 콘솔 appender 자체를 갈아야 한다.
 *
 * <p>logging.config가 유효한 이유: EnvironmentPostProcessor를 실행하는
 * EnvironmentPostProcessorApplicationListener가 LoggingApplicationListener보다 먼저 동작하므로
 * (HIGHEST_PRECEDENCE+10 vs +20), 로깅 시스템 초기화 시점에 이 값이 이미 환경에 들어와 있다.
 * 값 안의 플레이스홀더는 LoggingApplicationListener가 resolvePlaceholders로 해석한다.
 */
public class SwtpObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "swtpObservabilityDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "management.endpoints.web.exposure.include", "health,info,prometheus,metrics",
            "management.metrics.tags.application", "${spring.application.name}",
            // 콘솔 포맷 스위치 — 정수장별 차이를 배포 환경변수 한 줄로 흡수한다(swtp.auth.mode와 같은 패턴).
            // 기본 plain: 수집 파이프라인이 없는 정수장과 로컬 개발이 다수다. 수집하는 곳만 ecs를 명시한다.
            // config-repo가 이 키의 SSOT지만, config-repo를 읽지 않는 config-server도 같은 환경변수로 전환된다.
            "logging.config", "classpath:swtp-logback-${SWTP_LOG_FORMAT:plain}.xml",
            "swtp.logging.path", "logs");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
    }
}
