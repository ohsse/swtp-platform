package com.mo.swtp.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Job 서비스 — 자동(Quartz)/수동(REST) 실행이 동일 경로를 타는 통합 Job 실행기 */
@SpringBootApplication
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
