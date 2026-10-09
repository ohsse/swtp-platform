package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import com.mo.swtp.job.sample.SampleJobExecution.TriggerType;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz → launcher 위임체 — "언제 실행할 것인가"만 담당한다 (아키텍처 8.3).
 * Boot의 SpringBeanJobFactory가 인스턴스를 autowire하므로 생성자 주입이 아닌 필드 주입을 쓴다.
 */
public class SampleCollectQuartzJob implements Job {

    @Autowired
    private SampleJobLauncher launcher;

    @Override
    public void execute(JobExecutionContext context) {
        launcher.launch(SampleJobLauncher.JOB_SAMPLE_COLLECT, TriggerType.SCHEDULED, null);
    }
}
