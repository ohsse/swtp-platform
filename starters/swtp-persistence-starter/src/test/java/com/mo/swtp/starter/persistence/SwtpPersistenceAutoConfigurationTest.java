package com.mo.swtp.starter.persistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpPersistenceAutoConfigurationTest {

    // @EnableJpaAuditing이 등록하는 핸들러 빈 이름 (JpaAuditingRegistrar는 package-private이라 타입 참조 불가)
    private static final String JPA_AUDITING_HANDLER_BEAN = "jpaAuditingHandler";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SwtpPersistenceAutoConfiguration.class, SwtpJpaAuditingAutoConfiguration.class));

    @Test
    @DisplayName("자동구성이 마커 빈을 등록한다")
    void autoConfigurationRegistersMarkerBean() {
        runner.run(context -> assertThat(context).hasSingleBean(SwtpPersistenceStarterMarker.class));
    }

    @Test
    @DisplayName("EntityManagerFactory 빈이 없으면(JdbcClient 전용 앱) JPA Auditing을 켜지 않는다")
    void jpaAuditingSkippedWithoutEntityManagerFactoryBean() {
        // 클래스는 있으나 JPA 인프라가 구성되지 않은 상태 — auditing 핸들러는 jpaMappingContext를
        // 필요로 하므로, 여기서 켜지면 기동이 깨진다 (@ConditionalOnBean이 막는 시나리오)
        runner.run(context -> {
            assertThat(context).hasSingleBean(SwtpPersistenceStarterMarker.class);
            assertThat(context).doesNotHaveBean(JPA_AUDITING_HANDLER_BEAN);
        });
    }

    @Test
    @DisplayName("data-jpa가 클래스패스에 없어도 나머지 자동구성은 정상 로드된다")
    void loadsWithoutJpaOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(EntityManagerFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SwtpPersistenceStarterMarker.class);
                    assertThat(context).doesNotHaveBean(JPA_AUDITING_HANDLER_BEAN);
                });
    }

    @Test
    @DisplayName("등록 파일(imports/spring.factories)에 오타가 없다")
    void registrationFilesAreCorrect() throws Exception {
        assertThat(classpathContents("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .anySatisfy(content -> assertThat(content)
                        .contains(SwtpPersistenceAutoConfiguration.class.getName())
                        .contains(SwtpJpaAuditingAutoConfiguration.class.getName()));

        assertThat(classpathContents("META-INF/spring.factories"))
                .anySatisfy(content ->
                        assertThat(content).contains(SwtpPersistenceEnvironmentPostProcessor.class.getName()));
    }

    /**
     * 같은 이름의 리소스가 여러 JAR에 존재하므로(스프링 자신도 spring.factories를 가진다)
     * 첫 매치만 보면 안 된다. URL 스트림으로 읽어 JAR/디렉토리 구분 없이 전부 훑는다.
     */
    private static List<String> classpathContents(String resourceName) throws Exception {
        List<String> contents = new ArrayList<>();
        var urls = SwtpPersistenceAutoConfigurationTest.class.getClassLoader().getResources(resourceName);
        while (urls.hasMoreElements()) {
            try (var stream = urls.nextElement().openStream()) {
                contents.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    @Test
    @DisplayName("spy.properties는 SLF4J appender를 지정한다 — raw stdout 우회 방지")
    void spyPropertiesUsesSlf4jAppender() throws Exception {
        assertThat(classpathContents("spy.properties"))
                .anySatisfy(content -> assertThat(content)
                        .contains("appender=com.p6spy.engine.spy.appender.Slf4JLogger"));
    }
}
