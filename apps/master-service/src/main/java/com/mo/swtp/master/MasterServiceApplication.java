package com.mo.swtp.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 마스터데이터 서비스 — 플랜트/공정/설비/태그/코드의 소유자 */
@SpringBootApplication
public class MasterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterServiceApplication.class, args);
    }
}
