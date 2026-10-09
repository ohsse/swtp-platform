package com.mo.swtp.job.sample;

// [일회용 샘플] Phase 8 수직 슬라이스 검증용 — 실제 Job 이력 설계 시 패키지째 삭제한다.

import java.util.List;
import java.util.UUID;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.job.sample.SampleJobDtos.ExecuteRequest;
import com.mo.swtp.job.sample.SampleJobExecution.TriggerType;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 실행 + 이력 조회 API — 아키텍처 8.6 "202 Accepted + executionId 즉시 반환" 검증.
 * 경로는 서비스 자신의 노출 경로다 — gateway는 /job-service/**로 받아 RewritePath로 접두사를 벗겨 넘긴다.
 */
@RestController
@RequestMapping("/api/job/sample-executions")
@RequiredArgsConstructor
public class SampleJobController {

    private final SampleJobLauncher launcher;
    private final SampleJobExecutionService executionService;

    /** 수동 실행 — 본체는 비동기로 돌고 202 + STARTING 이력이 즉시 반환된다 */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SampleJobExecution> execute(@Valid @RequestBody ExecuteRequest request) {
        return ApiResponse.ok(launcher.launch(request.jobName(), TriggerType.MANUAL, request.requestedBy()));
    }

    /** 최근 실행 이력 — jobName 미지정 시 전체 */
    @GetMapping
    public ApiResponse<List<SampleJobExecution>> findRecent(@RequestParam(required = false) String jobName,
                                                            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(executionService.findRecent(jobName, limit));
    }

    /** 실행 단건 조회 — 202 이후 상태 추적 용도 (미존재 시 COMMON-404) */
    @GetMapping("/{executionId}")
    public ApiResponse<SampleJobExecution> get(@PathVariable UUID executionId) {
        return ApiResponse.ok(executionService.get(executionId));
    }
}
