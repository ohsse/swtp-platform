package com.mo.swtp.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 시계열 데이터 서비스 — telemetry 스키마(TimescaleDB hypertable)의 소유자 */
@SpringBootApplication
public class TelemetryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryServiceApplication.class, args);
    }
}
