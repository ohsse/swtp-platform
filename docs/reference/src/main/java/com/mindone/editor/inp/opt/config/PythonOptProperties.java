package com.mindone.editor.inp.opt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파이썬 최적화 모듈 연동 설정 프로퍼티({@code editor.python.*}).
 *
 * <p>최적화 실행 요청 시, BE 는 이력(hist)을 공유 DB 에 저장한 뒤 그 <b>이력 ID</b> 만 파이썬 API 로 전달한다.
 * 파이썬은 이 ID 로 별도 프로세스를 실행하므로, BE 는 응답을 기다리지 않는다(fire-and-forget).</p>
 *
 * @param baseUrl          파이썬 API 기본 URL (예: {@code http://localhost:8000})
 * @param optimizePath     최적화 실행 요청 경로 (예: {@code /optimize})
 * @param connectTimeoutMs 연결 타임아웃(ms)
 * @param readTimeoutMs    읽기 타임아웃(ms) — 파이썬이 즉시 응답(접수)만 하므로 짧게 둔다.
 */
@ConfigurationProperties(prefix = "editor.python")
public record PythonOptProperties(
        String baseUrl,
        String optimizePath,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public PythonOptProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8000";
        }
        if (optimizePath == null || optimizePath.isBlank()) {
            optimizePath = "/optimize";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 2000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 5000;
        }
    }
}
