package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import jakarta.validation.constraints.NotBlank;

/**
 * 실행 요청 DTO — 응답은 SampleJobExecution record를 그대로 쓴다.
 */
public final class SampleJobDtos {

    private SampleJobDtos() {
    }

    /** requestedBy는 인증 전 단계라 요청 본문 값 (auth 슬라이스에서 인증 주체로 대체) */
    public record ExecuteRequest(@NotBlank String jobName, String requestedBy) {
    }
}
