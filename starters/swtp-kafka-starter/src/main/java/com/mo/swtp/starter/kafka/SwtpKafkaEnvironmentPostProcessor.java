package com.mo.swtp.starter.kafka;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Kafka Producer/Consumer 직렬화·소비 기본값을 최저 우선순위로 주입한다 — 각 앱의 application.yml/환경변수가 항상 이긴다.
 *
 * <p>Producer: value는 Jackson 3(tools.jackson) 기반 JSON 직렬화(JacksonJsonSerializer).
 * Boot의 Kafka 자동구성(재시도/acks/SSL 등 전체 프로퍼티 표면)을 그대로 활용하기 위해
 * ProducerFactory/ConsumerFactory를 재정의하지 않고 프로퍼티 기본값만 계약으로 제공한다.
 *
 * <p>Consumer 규약 — "문자열 수신 + 명시적 파싱":
 * JacksonJsonDeserializer의 __TypeId__ 헤더 방식은 소비측이 발행측 클래스명에 결합되므로 쓰지 않는다.
 * 서비스 간 계약은 JSON 구조로만 유지하고, 소비측이 {@code EventEnvelopeParser}로 명시적으로 파싱한다.
 */
public class SwtpKafkaEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "swtpKafkaDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "spring.kafka.producer.key-serializer",
            "org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer",
            "org.springframework.kafka.support.serializer.JacksonJsonSerializer",
            "spring.kafka.consumer.key-deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.consumer.value-deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer",
            // 서비스별 독립 컨슈머 그룹 기본값 — placeholder는 프로퍼티 조회 시점에 해석된다
            "spring.kafka.consumer.group-id", "${spring.application.name}",
            // 적재 파이프라인 안전 기본값 — 브로드캐스트성 소비자는 latest로 재정의한다
            "spring.kafka.consumer.auto-offset-reset", "earliest");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
    }
}
