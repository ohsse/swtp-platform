package com.mo.swtp.starter.kafka.event;

import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트 봉투 파싱 표준 진입점 — Consumer 규약("문자열 수신 + 명시적 파싱")의 파싱 담당.
 * 리스너는 String으로 받은 메시지를 payload 타입을 지정해 이 파서로 해석한다.
 *
 * <p>발행측 클래스명(__TypeId__ 헤더)에 의존하지 않으므로 서비스 간 계약은 JSON 구조로만 유지된다.
 */
public class EventEnvelopeParser {

    // Jackson 3(tools.jackson)는 java.time 지원이 기본 내장 — occurredAt(Instant) 별도 모듈 불필요
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 봉투 JSON을 지정한 payload 타입으로 파싱한다.
     *
     * @param json        봉투 전체 JSON 문자열
     * @param payloadType payload 슬롯의 계약 타입
     * @throws tools.jackson.core.JacksonException 구조 불일치 시 (Jackson 3는 unchecked)
     */
    public <T> EventEnvelope<T> parse(String json, Class<T> payloadType) {
        return jsonMapper.readValue(json,
                jsonMapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType));
    }
}
