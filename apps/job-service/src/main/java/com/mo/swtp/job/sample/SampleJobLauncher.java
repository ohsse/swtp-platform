package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.util.Set;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.job.sample.SampleJobExecution.TriggerType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * 자동(Quartz)/수동(REST)이 수렴하는 단일 실행 진입점 — 아키텍처 8.5 JobExecutionService 패턴.
 *
 * <p>의도적으로 <b>비트랜잭션</b>이다: create()의 독립 트랜잭션이 커밋을 마친 뒤에야
 * 비동기 실행을 제출해야, @Async 스레드가 커밋 전 이력 row를 못 보는 경합이 원천 차단된다.
 */
@Component
@RequiredArgsConstructor
public class SampleJobLauncher {

    public static final String JOB_SAMPLE_COLLECT = "sample-collect";
    public static final String JOB_SAMPLE_AGGREGATE = "sample-aggregate";

    private static final Set<String> KNOWN_JOBS = Set.of(JOB_SAMPLE_COLLECT, JOB_SAMPLE_AGGREGATE);

    private final SampleJobExecutionService executionService;
    private final SampleJobRunner runner;

    /** 이력 등록(STARTING, 커밋 완료) → 비동기 실행 제출 → 즉시 반환 (호출자는 202 + executionId) */
    public SampleJobExecution launch(String jobName, TriggerType triggerType, String requestedBy) {
        if (!KNOWN_JOBS.contains(jobName)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "job %s 없음".formatted(jobName));
        }
        SampleJobExecution execution = executionService.create(jobName, triggerType, requestedBy);
        runner.runAsync(execution.executionId(), jobName);
        return execution;
    }
}
