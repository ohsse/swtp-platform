package com.mo.swtp.starter.observability;

import java.nio.file.Files;
import java.nio.file.Path;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통 logback 설정을 <b>실제 Boot 기동 경로</b>로 검증한다.
 *
 * <p>일반 {@code JoranConfigurator}로는 검증할 수 없다 — {@code <springProperty>}는 Boot 전용 태그라
 * {@code SpringBootJoranConfigurator}가 아니면 <b>오류 없이 조용히 무시</b>되고 fallback 값이 쓰인다.
 * 그래서 최소 애플리케이션을 띄워 로깅 시스템 초기화까지 통과시킨다.
 *
 * <p>로그 출력은 {@code @TempDir}가 아니라 build 디렉토리에 쓴다 — Windows에서는 컨텍스트를 닫아도
 * RollingFileAppender의 파일 핸들이 남아 임시 디렉토리 삭제가 실패한다.
 */
class SwtpLogbackConfigurationTest {

    private static final Path LOG_ROOT = Path.of("build", "test-logs");

    @SpringBootApplication
    static class TestApp {
    }

    /** 테스트별로 독립된 로그 디렉토리 — 이전 실행 잔재가 단언을 오염시키지 않게 비우고 시작한다 */
    private Path logPathFor(TestInfo testInfo) throws Exception {
        Path path = LOG_ROOT.resolve(testInfo.getTestMethod().orElseThrow().getName());
        if (Files.exists(path)) {
            try (var paths = Files.walk(path)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        return path;
    }

    private ConfigurableApplicationContext run(Path logPath, String... extraProperties) {
        return new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties("spring.application.name=test-service")
                // Windows 역슬래시는 logback 프로퍼티 치환에서 이스케이프로 오인될 수 있다
                .properties("swtp.logging.path=" + logPath.toAbsolutePath().toString().replace('\\', '/'))
                .properties(extraProperties)
                .run();
    }

    @Test
    @DisplayName("EnvironmentPostProcessor가 주입한 logging.config로 CONSOLE/FILE/ERROR_FILE이 구성된다")
    void starterDefaultsConfigureAllAppenders(TestInfo testInfo) throws Exception {
        // logging.config를 명시하지 않는다 — 스타터 기본값 주입이 실제로 먹히는지가 검증 대상
        try (var context = run(logPathFor(testInfo))) {
            assertThat(context.getEnvironment().getProperty("logging.config"))
                    .isEqualTo("classpath:swtp-logback-plain.xml");

            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

            assertThat(root.getAppender("CONSOLE")).as("사람이 읽는 콘솔 경로").isNotNull();
            assertThat(root.getAppender("FILE")).as("일반 로그 파일").isInstanceOf(RollingFileAppender.class);
            assertThat(root.getAppender("ERROR_FILE")).as("에러 전용 파일").isInstanceOf(RollingFileAppender.class);
        }
    }

    @Test
    @DisplayName("기본(plain)은 콘솔도 평문이다")
    void plainFormatUsesPatternEncoderOnConsole(TestInfo testInfo) throws Exception {
        try (var context = run(logPathFor(testInfo))) {
            assertThat(consoleEncoderTypeName()).isEqualTo("PatternLayoutEncoder");
        }
    }

    @Test
    @DisplayName("SWTP_LOG_FORMAT=ecs면 콘솔만 구조화되고 파일은 평문으로 남는다")
    void ecsFormatStructuresConsoleOnly(TestInfo testInfo) throws Exception {
        // 포맷 스위치는 프로퍼티가 아니라 logback 파일 자체를 고른다 —
        // structured-console-appender.xml이 encoder를 하드코딩하므로 프로퍼티로는 되돌릴 수 없기 때문이다
        try (var context = run(logPathFor(testInfo), "SWTP_LOG_FORMAT=ecs")) {
            assertThat(context.getEnvironment().getProperty("logging.config"))
                    .isEqualTo("classpath:swtp-logback-ecs.xml");

            assertThat(consoleEncoderTypeName())
                    .as("수집기가 읽는 콘솔만 ECS JSON")
                    .isEqualTo("StructuredLogEncoder");

            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            var fileAppender = (RollingFileAppender<?>) root.getAppender("FILE");
            assertThat(fileAppender.getEncoder().getClass().getSimpleName())
                    .as("파일은 어느 포맷에서든 평문 — 사람이 직접 열어 보는 백업")
                    .isEqualTo("PatternLayoutEncoder");
        }
    }

    /** Boot의 StructuredLogEncoder는 컴파일 의존을 만들 만한 공개 API가 아니라 타입명으로 판정한다 */
    private String consoleEncoderTypeName() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        var console = (ConsoleAppender<?>) root.getAppender("CONSOLE");
        return console.getEncoder().getClass().getSimpleName();
    }

    @Test
    @DisplayName("파일은 서비스명 디렉토리에 일반/에러로 분리되고, 에러 파일은 ERROR 이상만 남긴다")
    void errorFileFiltersBelowError(TestInfo testInfo) throws Exception {
        Path logPath = logPathFor(testInfo);
        try (var context = run(logPath)) {
            var logger = LoggerFactory.getLogger(SwtpLogbackConfigurationTest.class);
            logger.info("정상 동작 로그");
            logger.error("장애 로그");
        }
        // 컨텍스트 종료 시 로깅 시스템이 정리되며 appender가 flush된다

        Path logDir = logPath.resolve("test-service");
        assertThat(logDir).as("서비스명 하위 디렉토리 — springProperty 해석 증명").isDirectory();

        String general = Files.readString(logDir.resolve("test-service.log"));
        String errorOnly = Files.readString(logDir.resolve("test-service-error.log"));

        assertThat(general).contains("정상 동작 로그").contains("장애 로그");
        assertThat(errorOnly).contains("장애 로그").doesNotContain("정상 동작 로그");
    }

    @Test
    @DisplayName("롤링 정책 기본값(30일 보관)이 적용되고 앱이 재정의할 수 있다")
    void rollingPolicyDefaultsAreOverridable(TestInfo testInfo) throws Exception {
        try (var context = run(logPathFor(testInfo), "swtp.logging.max-history=7")) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

            var fileAppender = (RollingFileAppender<?>) root.getAppender("FILE");
            var policy = (SizeAndTimeBasedRollingPolicy<?>) fileAppender.getRollingPolicy();

            assertThat(policy.getMaxHistory()).as("앱 설정이 스타터 기본값 30을 재정의").isEqualTo(7);
            assertThat(fileAppender.getFile()).endsWith("test-service.log");
        }
    }
}
