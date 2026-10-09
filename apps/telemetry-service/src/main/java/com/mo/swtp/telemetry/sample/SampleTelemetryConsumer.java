package com.mo.swtp.telemetry.sample;

import com.mo.swtp.starter.kafka.event.EventEnvelope;
import com.mo.swtp.starter.kafka.event.EventEnvelopeParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * [일회용 샘플] telemetry.raw 적재 컨슈머 — 실제 telemetry 설계 착수 시 폐기한다.
 *
 * <p>Consumer 규약("문자열 수신 + 명시적 파싱")의 표준형:
 * String으로 받아 EventEnvelopeParser로 payload 타입을 지정해 해석한다.
 * 그룹/오프셋은 kafka-starter 기본값(그룹=앱 이름, earliest)을 그대로 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleTelemetryConsumer {

    private final EventEnvelopeParser parser;
    private final SampleMeasurementRepository repository;

    @KafkaListener(topics = "telemetry.raw")
    public void consume(String message) {
        EventEnvelope<SampleMeasurement> envelope = parser.parse(message, SampleMeasurement.class);
        repository.insert(envelope.payload());
        log.debug("적재 완료: eventId={} tagId={}", envelope.eventId(), envelope.payload().tagId());
    }
}
