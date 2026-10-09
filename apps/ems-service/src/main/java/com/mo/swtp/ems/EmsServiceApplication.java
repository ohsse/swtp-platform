package com.mo.swtp.ems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** EMS 서비스 — 에너지 사용 관리/분석 (Optional 배포) */
@SpringBootApplication
public class EmsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmsServiceApplication.class, args);
    }
}
