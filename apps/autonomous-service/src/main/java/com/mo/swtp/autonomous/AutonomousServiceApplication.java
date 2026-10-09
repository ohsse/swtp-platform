package com.mo.swtp.autonomous;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 자율운영 서비스 — AI Service(FastAPI)와 연계하여 운영 판단을 수행 (Optional 배포) */
@SpringBootApplication
public class AutonomousServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutonomousServiceApplication.class, args);
    }
}
