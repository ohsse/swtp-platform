package com.mo.swtp.starter.kafka;

import com.mo.swtp.starter.kafka.event.EventEnvelopeParser;
import com.mo.swtp.starter.kafka.event.SwtpEventPublisher;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka 공통 자동구성 — 이벤트 발행(SwtpEventPublisher)/파싱(EventEnvelopeParser) 표준 진입점 제공.
 * KafkaTemplate 자체는 Boot 자동구성이 만든다 (직렬화·소비 기본값은 {@link SwtpKafkaEnvironmentPostProcessor}).
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class SwtpKafkaAutoConfiguration {

    /** 표준 이벤트 퍼블리셔 — KafkaTemplate이 구성된 경우에만 등록 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    public SwtpEventPublisher swtpEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 Environment environment) {
        return new SwtpEventPublisher(kafkaTemplate,
                environment.getProperty("spring.application.name", "unknown"));
    }

    /** 봉투 파싱 표준 진입점 — Consumer 규약("문자열 수신 + 명시적 파싱")의 파서 */
    @Bean
    @ConditionalOnMissingBean
    public EventEnvelopeParser eventEnvelopeParser() {
        return new EventEnvelopeParser();
    }

    /** 자동구성 로드 여부를 확인하기 위한 마커 빈 */
    @Bean
    public SwtpKafkaStarterMarker swtpKafkaStarterMarker() {
        return new SwtpKafkaStarterMarker();
    }
}
