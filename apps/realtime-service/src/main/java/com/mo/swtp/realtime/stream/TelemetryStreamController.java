package com.mo.swtp.realtime.stream;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독 엔드포인트 — gateway 라우트(/realtime-service/**, RewritePath로 접두사를 벗김)를
 * 경유해 Frontend가 연결한다. 즉 게이트웨이에서 본 주소는 /realtime-service/api/realtime/... 다.
 * 스트림 응답이므로 ApiResponse 봉투 규약의 대상이 아니다.
 */
@RestController
@RequestMapping("/api/realtime/telemetry")
@RequiredArgsConstructor
public class TelemetryStreamController {

    private final TelemetryStreamBroadcaster broadcaster;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
