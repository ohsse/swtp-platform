package com.mo.swtp.starter.web;

import java.io.IOException;
import java.util.UUID;

import com.mo.swtp.common.observability.SwtpHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 게이트웨이가 전파한 요청 ID를 MDC에 올려, 이 서비스가 남기는 모든 로그에 상관관계 ID를 싣는다.
 * 구조화 로깅(ECS)이 MDC를 자동 포함하므로 로그 검색 백엔드에서 게이트웨이 로그와 서비스 로그를 한 ID로 묶을 수 있다.
 *
 * <p>게이트웨이를 거치지 않은 직접 호출(서비스 간 내부 호출, 로컬 테스트)에는 헤더가 없으므로
 * 이 필터가 자체 발급한다 — ID 없는 로그가 생기지 않게 하는 것이 목적이다.
 */
public class RequestIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(SwtpHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(SwtpHeaders.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(SwtpHeaders.REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 풀 재사용 환경에서 이전 요청의 ID가 남지 않도록 반드시 제거한다
            MDC.remove(SwtpHeaders.REQUEST_ID_MDC_KEY);
        }
    }
}
