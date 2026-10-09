package com.mo.swtp.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 실시간 서비스 — Kafka 이벤트를 SSE/WebSocket으로 프론트에 브로드캐스트 */
@SpringBootApplication
public class RealtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}
