package com.mo.swtp.realtime.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * telemetry.raw → SSE 중계 컨슈머 — 실시간 전달의 영구 골격 (샘플 아님).
 *
 * <p>봉투 JSON을 파싱하지 않고 그대로 전달한다 — payload 스키마에 비결합이므로
 * 샘플 도메인 폐기나 실도메인 전환의 영향을 받지 않는다. 해석은 Frontend 책임.
 * 오프셋은 latest(앱 yml 재정의) — 브로드캐스트는 과거 재생이 불필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryStreamConsumer {

    private final TelemetryStreamBroadcaster broadcaster;

    @KafkaListener(topics = "telemetry.raw")
    public void relay(String envelopeJson) {
        broadcaster.broadcast(envelopeJson);
    }
}
