package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 집계 Batch Job 정의 — @EnableBatchProcessing을 붙이면 Boot 자동구성이 꺼지므로 금지.
 *
 * <p>단일 Tasklet(UPSERT 한 방)인 이유: 전량 재집계 SQL이 이미 멱등이라 chunk 지향(reader/writer)은
 * 샘플 목적 대비 과대 — 대량 chunk 처리는 실설계(AI Dataset 등)에서 도입한다.
 */
@Configuration
public class SampleAggregateBatchConfig {

    @Bean
    public Job sampleAggregateBatchJob(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       SampleCollectedMeasurementRepository measurementRepository) {
        Step aggregateStep = new StepBuilder("sample-aggregate-step", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    measurementRepository.aggregate();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
        return new JobBuilder(SampleJobLauncher.JOB_SAMPLE_AGGREGATE, jobRepository)
                .start(aggregateStep)
                .build();
    }
}
