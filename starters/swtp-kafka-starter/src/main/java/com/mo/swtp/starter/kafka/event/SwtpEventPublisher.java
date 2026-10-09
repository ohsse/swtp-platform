package com.mo.swtp.starter.kafka.event;

import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * 이벤트 발행 표준 진입점 — KafkaTemplate을 직접 쓰지 않고 이 파사드를 통해
 * 항상 EventEnvelope로 감싸서 발행한다.
 */
@RequiredArgsConstructor
public class SwtpEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /** 발행 서비스 이름 — 봉투의 source 슬롯에 실린다 */
    private final String source;

    /**
     * 이벤트를 봉투로 감싸 발행한다.
     *
     * @param topic     대상 토픽 (예: "master.changed")
     * @param key       파티션 키 — 같은 키는 같은 파티션으로 가서 순서가 보장된다 (예: 엔티티 id)
     * @param eventType 이벤트 유형 (예: "sample-item.created")
     * @param payload   이벤트 본문
     */
    public CompletableFuture<SendResult<Object, Object>> publish(
            String topic, String key, String eventType, Object payload) {
        return kafkaTemplate.send(topic, key, EventEnvelope.of(eventType, source, payload));
    }
}
