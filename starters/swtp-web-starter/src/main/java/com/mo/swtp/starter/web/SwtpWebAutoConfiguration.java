package com.mo.swtp.starter.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 웹 계층 공통 자동구성 — 전역 예외 처리 + 요청 상관관계 ID 규약.
 * SERVLET 조건: gateway(WebFlux) 등 비서블릿 앱을 오염시키지 않는다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SwtpWebAutoConfiguration {

    /** 전역 예외 핸들러 — 앱이 자체 핸들러 빈을 정의하면 물러난다 */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler swtpGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 요청 ID를 MDC에 올리는 필터 — 최우선 순위로 등록해야 이후 모든 로그가 ID를 갖는다.
     * (예외 처리 중 남기는 로그도 포함되도록 예외 핸들러보다 바깥에 위치한다)
     */
    @Bean
    @ConditionalOnMissingBean(name = "swtpRequestIdMdcFilterRegistration")
    public FilterRegistrationBean<RequestIdMdcFilter> swtpRequestIdMdcFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new RequestIdMdcFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** 자동구성 로드 여부를 확인하기 위한 마커 빈 */
    @Bean
    public SwtpWebStarterMarker swtpWebStarterMarker() {
        return new SwtpWebStarterMarker();
    }
}
