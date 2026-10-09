package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import com.mo.swtp.job.sample.SampleJobExecution.TriggerType;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz → launcher 위임체 (10분 집계) — SampleCollectQuartzJob과 동일 구조.
 */
public class SampleAggregateQuartzJob implements Job {

    @Autowired
    private SampleJobLauncher launcher;

    @Override
    public void execute(JobExecutionContext context) {
        launcher.launch(SampleJobLauncher.JOB_SAMPLE_AGGREGATE, TriggerType.SCHEDULED, null);
    }
}
