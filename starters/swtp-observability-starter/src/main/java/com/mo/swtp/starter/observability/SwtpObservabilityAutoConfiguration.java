package com.mo.swtp.starter.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 관측성(로그/메트릭) 공통 자동구성 골격.
 * 슬라이스 구현 시 공통 메트릭 태그, 구조화 로그 포맷 등을 채운다.
 */
@AutoConfiguration
public class SwtpObservabilityAutoConfiguration {

    /** 자동구성 로드 여부를 확인하기 위한 마커 빈 */
    @Bean
    public SwtpObservabilityStarterMarker swtpObservabilityStarterMarker() {
        return new SwtpObservabilityStarterMarker();
    }
}
