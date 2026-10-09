package com.mo.swtp.starter.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 플랫폼 공통 이벤트 봉투 — 모든 토픽의 메시지는 이 구조로 감싸서 발행한다.
 * 컨슈머는 eventType으로 분기하고, payload는 이벤트별 스키마를 따른다.
 *
 * @param eventId    이벤트 고유 ID (UUID) — 중복 처리 감지용
 * @param eventType  이벤트 유형 (예: "sample-item.created")
 * @param source     발행 서비스 이름 (spring.application.name)
 * @param occurredAt 발생 시각 (UTC)
 * @param payload    이벤트 본문
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String source,
        Instant occurredAt,
        T payload) {

    /** 발행 시점 메타데이터(eventId/occurredAt)를 채워 봉투를 만든다 */
    public static <T> EventEnvelope<T> of(String eventType, String source, T payload) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, source, Instant.now(), payload);
    }
}
