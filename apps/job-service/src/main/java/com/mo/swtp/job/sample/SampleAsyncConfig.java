package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 202 즉시 반환을 위한 @Async 활성화 — 애플리케이션 클래스가 아닌 sample 패키지에 두어 폐기가 쉽다.
 * 실행 스레드는 Boot 기본 applicationTaskExecutor를 쓴다.
 */
@Configuration
@EnableAsync
public class SampleAsyncConfig {
}
