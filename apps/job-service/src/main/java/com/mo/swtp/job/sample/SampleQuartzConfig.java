package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz 스케줄 정의 — cron은 프로퍼티(swtp.sample-job.*)로 외부화해 테스트가 재정의한다.
 * JobDetail/Trigger 빈은 Boot 자동구성이 JDBC store에 등록하고,
 * overwrite-existing-jobs=true라 재기동 시 코드 정의가 store의 구 값을 이긴다.
 */
@Configuration
public class SampleQuartzConfig {

    @Bean
    public JobDetail sampleCollectJobDetail() {
        return JobBuilder.newJob(SampleCollectQuartzJob.class)
                .withIdentity("sample-collect-quartz")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger sampleCollectTrigger(JobDetail sampleCollectJobDetail,
                                        @Value("${swtp.sample-job.collect-cron}") String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(sampleCollectJobDetail)
                .withIdentity("sample-collect-trigger")
                // misfire(기동 지연 등)는 건너뛴다 — 밀린 발화 몰아치기 방지
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).withMisfireHandlingInstructionDoNothing())
                .build();
    }

    @Bean
    public JobDetail sampleAggregateJobDetail() {
        return JobBuilder.newJob(SampleAggregateQuartzJob.class)
                .withIdentity("sample-aggregate-quartz")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger sampleAggregateTrigger(JobDetail sampleAggregateJobDetail,
                                          @Value("${swtp.sample-job.aggregate-cron}") String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(sampleAggregateJobDetail)
                .withIdentity("sample-aggregate-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
