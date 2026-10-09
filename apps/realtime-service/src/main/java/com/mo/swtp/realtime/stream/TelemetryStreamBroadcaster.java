package com.mo.swtp.realtime.stream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독자 레지스트리 + 브로드캐스트 — 실시간 전달의 영구 골격 (샘플 아님).
 *
 * <p>Phase 7 범위: 전체 브로드캐스트만. 태그별 필터링/인증은 실도메인·auth 슬라이스에서 다룬다.
 */
@Slf4j
@Component
public class TelemetryStreamBroadcaster {

    /** 구독 타임아웃 30분 — 만료 시 클라이언트(EventSource)가 재연결할 책임을 가진다 */
    private static final long SUBSCRIPTION_TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 구독 등록 — 완료/타임아웃/에러 시 레지스트리에서 자동 제거된다 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SUBSCRIPTION_TIMEOUT_MILLIS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            // 주석 한 줄로 응답 헤더를 즉시 flush — 첫 이벤트 전까지 클라이언트가 연결 성공(onopen)을 모르는 문제 방지
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        log.debug("SSE 구독 등록 — 현재 {}건", emitters.size());
        return emitter;
    }

    /** 모든 구독자에게 봉투 JSON을 그대로 전달한다 — 전송 실패한 구독자는 제거 */
    public void broadcast(String envelopeJson) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("telemetry").data(envelopeJson));
            } catch (Exception e) {
                // 끊긴 클라이언트 — 레지스트리 정리 (onError/onCompletion 콜백도 제거를 시도하므로 멱등)
                emitters.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }

    /** 현재 구독자 수 — 모니터링/테스트 보조 */
    public int subscriberCount() {
        return emitters.size();
    }
}
