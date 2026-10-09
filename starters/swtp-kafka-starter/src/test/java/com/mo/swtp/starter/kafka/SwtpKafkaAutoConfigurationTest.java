package com.mo.swtp.starter.kafka;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import com.mo.swtp.starter.kafka.event.EventEnvelope;
import com.mo.swtp.starter.kafka.event.EventEnvelopeParser;
import com.mo.swtp.starter.kafka.event.SwtpEventPublisher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpKafkaAutoConfigurationTest {

    // Boot Kafka 자동구성과 함께 구동 — KafkaTemplate 생성은 lazy라 실제 브로커는 불필요
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class, SwtpKafkaAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    @DisplayName("KafkaTemplate이 있으면 표준 퍼블리셔와 마커 빈이 등록된다")
    void registersPublisherAndMarker() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(KafkaTemplate.class);
            assertThat(context).hasSingleBean(SwtpEventPublisher.class);
            assertThat(context).hasSingleBean(EventEnvelopeParser.class);
            assertThat(context).hasSingleBean(SwtpKafkaStarterMarker.class);
        });
    }

    @Test
    @DisplayName("이벤트 봉투 팩토리는 eventId/occurredAt 메타데이터를 채운다")
    void envelopeFactoryFillsMetadata() {
        EventEnvelope<String> envelope = EventEnvelope.of("sample-item.created", "master-service", "payload");

        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.eventType()).isEqualTo("sample-item.created");
        assertThat(envelope.source()).isEqualTo("master-service");
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    @Test
    @DisplayName("직렬화 기본값은 최저 우선순위 — 기존 프로퍼티 소스가 항상 이긴다")
    void serializerDefaultsHaveLowestPrecedence() {
        var environment = new StandardEnvironment();
        new SwtpKafkaEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        // 기본값 주입 확인 — value는 Jackson 3 기반 JacksonJsonSerializer
        assertThat(environment.getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo("org.springframework.kafka.support.serializer.JacksonJsonSerializer");
        assertThat(environment.getProperty("spring.kafka.producer.key-serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        // addLast로 주입되므로 앱 설정(먼저 등록된 소스)이 우선한다
        assertThat(environment.getPropertySources().stream().toList().getLast().getName())
                .isEqualTo(SwtpKafkaEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }

    @Test
    @DisplayName("Consumer 기본값 — String 역직렬화 + 앱 이름 그룹 + earliest, 앱 설정이 재정의할 수 있다")
    void consumerDefaultsFollowContract() {
        var environment = new StandardEnvironment();
        // 앱 설정 시뮬레이션 — EPP보다 먼저 등록된 소스가 이긴다
        environment.getPropertySources().addFirst(new MapPropertySource("app", Map.of(
                "spring.application.name", "telemetry-service",
                "spring.kafka.consumer.auto-offset-reset", "latest")));
        new SwtpKafkaEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.kafka.consumer.key-deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(environment.getProperty("spring.kafka.consumer.value-deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        // group-id 기본값의 placeholder는 조회 시점에 앱 이름으로 해석된다
        assertThat(environment.getProperty("spring.kafka.consumer.group-id"))
                .isEqualTo("telemetry-service");
        // 앱 설정이 스타터 기본값(earliest)을 이긴다
        assertThat(environment.getProperty("spring.kafka.consumer.auto-offset-reset"))
                .isEqualTo("latest");
    }

    @Test
    @DisplayName("봉투 파서는 payload 타입을 지정한 명시적 파싱으로 봉투 JSON을 해석한다")
    void parserParsesEnvelopeJson() {
        record SamplePayload(String tagId, double value) {
        }
        String json = """
                {
                  "eventId": "e-1",
                  "eventType": "sample-measurement.recorded",
                  "source": "job-service",
                  "occurredAt": "2026-08-12T01:00:00Z",
                  "payload": {"tagId": "탁도-001", "value": 0.34}
                }""";

        EventEnvelope<SamplePayload> envelope =
                new EventEnvelopeParser().parse(json, SamplePayload.class);

        assertThat(envelope.eventId()).isEqualTo("e-1");
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-08-12T01:00:00Z"));
        assertThat(envelope.payload()).isEqualTo(new SamplePayload("탁도-001", 0.34));
    }

    @Test
    @DisplayName("자동구성/EnvironmentPostProcessor 등록 파일에 오타가 없다")
    void registrationFilesAreCorrect() throws Exception {
        var imports = getClass().getClassLoader()
                .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(imports).isNotNull();
        assertThat(Files.readString(Path.of(imports.toURI())))
                .contains(SwtpKafkaAutoConfiguration.class.getName());

        var factories = getClass().getClassLoader().getResource("META-INF/spring.factories");
        assertThat(factories).isNotNull();
        assertThat(Files.readString(Path.of(factories.toURI())))
                .contains(SwtpKafkaEnvironmentPostProcessor.class.getName());
    }
}
