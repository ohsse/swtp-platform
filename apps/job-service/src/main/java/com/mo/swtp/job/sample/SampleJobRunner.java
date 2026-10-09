package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 실행 본체 — launcher와 분리된 빈이어야 @Async 프록시가 적용된다 (자기호출 금지).
 * 상태 전이는 전이별 독립 트랜잭션(SampleJobExecutionService)이라, 오래 도는 본체와 무관하게 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleJobRunner {

    private final SampleJobExecutionService executionService;
    private final SampleCollector collector;
    // Batch 6에서 JobLauncher는 deprecated — JobOperator가 현행 실행 인터페이스 (Boot 4.1 자동구성 빈)
    private final JobOperator batchJobOperator;
    private final Job sampleAggregateBatchJob;

    @Async
    public void runAsync(UUID executionId, String jobName) {
        try {
            executionService.markRunning(executionId);
            switch (jobName) {
                case SampleJobLauncher.JOB_SAMPLE_COLLECT -> collector.collect();
                case SampleJobLauncher.JOB_SAMPLE_AGGREGATE -> runAggregateBatch(executionId);
                default -> throw new IllegalStateException("등록되지 않은 job: " + jobName);
            }
            executionService.complete(executionId);
        } catch (Exception e) {
            log.error("샘플 Job 실행 실패 — executionId={}, jobName={}", executionId, jobName, e);
            executionService.fail(executionId, e.getMessage());
        }
    }

    /**
     * Batch는 이 스레드에서 동기 실행된다 — @BatchTaskExecutor 빈이 없으면 Boot 기본이 동기.
     * JobParameters의 executionId가 매 실행을 유니크하게 하고(인스턴스 중복 방지), 이력 상호 참조 키가 된다.
     */
    private void runAggregateBatch(UUID executionId) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addString("executionId", executionId.toString())
                .toJobParameters();
        JobExecution batchExecution = batchJobOperator.start(sampleAggregateBatchJob, parameters);
        if (batchExecution.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException("Batch 종료 상태 %s — %s".formatted(
                    batchExecution.getStatus(), batchExecution.getExitStatus().getExitDescription()));
        }
    }
}
