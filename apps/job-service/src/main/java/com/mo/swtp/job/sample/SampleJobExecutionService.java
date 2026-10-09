package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.job.sample.SampleJobExecution.Status;
import com.mo.swtp.job.sample.SampleJobExecution.TriggerType;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실행 이력 상태 전이 — 아키텍처 8.5 "자동/수동 동일 경로"의 이력 계층.
 *
 * <p>전이마다 독립 트랜잭션인 이유: 실행 본체는 비동기 스레드에서 오래 돌기 때문에
 * 하나의 트랜잭션으로 묶을 수 없고, 각 전이가 커밋 확정되어야 릴레이(AFTER_COMMIT)가 job.event를 발행한다.
 */
@Service
@RequiredArgsConstructor
public class SampleJobExecutionService {

    private final SampleJobExecutionRepository repository;
    private final ApplicationEventPublisher domainEventPublisher;

    /** STARTING 이력 생성 + STARTED 이벤트 — launcher가 이 트랜잭션의 커밋을 보고 비동기 실행을 제출한다 */
    @Transactional
    public SampleJobExecution create(String jobName, TriggerType triggerType, String requestedBy) {
        SampleJobExecution execution = new SampleJobExecution(
                UUID.randomUUID(), jobName, triggerType, Status.STARTING, requestedBy, Instant.now(), null, null);
        repository.insert(execution);
        domainEventPublisher.publishEvent(
                new SampleJobExecutionChangedEvent(SampleJobExecutionChangedEvent.STARTED, execution));
        return execution;
    }

    /** RUNNING 전이 — 외부 관심사가 아니라서 이벤트는 발행하지 않는다 */
    @Transactional
    public void markRunning(UUID executionId) {
        repository.markRunning(executionId);
    }

    @Transactional
    public void complete(UUID executionId) {
        finish(executionId, Status.COMPLETED, null, SampleJobExecutionChangedEvent.COMPLETED);
    }

    @Transactional
    public void fail(UUID executionId, String failureReason) {
        finish(executionId, Status.FAILED, failureReason, SampleJobExecutionChangedEvent.FAILED);
    }

    private void finish(UUID executionId, Status status, String failureReason, String eventType) {
        repository.markFinished(executionId, status, Instant.now(), failureReason);
        SampleJobExecution updated = getOrThrow(executionId);
        domainEventPublisher.publishEvent(new SampleJobExecutionChangedEvent(eventType, updated));
    }

    @Transactional(readOnly = true)
    public SampleJobExecution get(UUID executionId) {
        return getOrThrow(executionId);
    }

    @Transactional(readOnly = true)
    public List<SampleJobExecution> findRecent(String jobName, int limit) {
        return repository.findRecent(jobName, limit);
    }

    private SampleJobExecution getOrThrow(UUID executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.NOT_FOUND, "job execution %s 없음".formatted(executionId)));
    }
}
