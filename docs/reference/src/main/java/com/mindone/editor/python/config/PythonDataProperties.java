package com.mindone.editor.python.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파이썬 데이터 조회/계산 모듈 연동 설정 프로퍼티({@code editor.python-data.*}).
 *
 * <p>기존 최적화 모듈({@code editor.python.*}, {@link com.mindone.editor.inp.opt.config.PythonOptProperties})과
 * <b>독립된 두 번째 파이썬 API</b>(개발 tenant 기준 30093 포트)이다.
 * 최적화가 fire-and-forget(접수만 확인)인 것과 달리, 이 모듈은 <b>동기 호출</b>로 결과(JSON)를
 * 즉시 받아 사용하므로 읽기 타임아웃을 충분히 길게 둔다.</p>
 *
 * @param baseUrl          파이썬 데이터 API 기본 URL (예: {@code http://localhost:30093})
 * @param connectTimeoutMs 연결 타임아웃(ms)
 * @param readTimeoutMs    읽기 타임아웃(ms) — 동기 계산 결과를 기다리므로 길게 둔다.
 */
@ConfigurationProperties(prefix = "editor.python-data")
public record PythonDataProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public PythonDataProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8091";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 2000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 30000;
        }
    }
}
