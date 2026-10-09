package com.mo.swtp.telemetry.sample;

import java.time.Instant;
import java.util.List;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [일회용 샘플] 시계열 조회 API — 실제 telemetry 설계 착수 시 폐기한다.
 * 아키텍처 문서 5.2의 "기간 데이터 조회 / 현재값 조회"를 샘플 도메인으로 관통 검증한다.
 */
@RestController
@RequestMapping("/api/telemetry/sample-measurements")
@RequiredArgsConstructor
public class SampleTelemetryController {

    private final SampleMeasurementRepository repository;

    /** 기간 조회 — from/to는 ISO-8601 (예: 2026-08-12T00:00:00Z) */
    @GetMapping
    public ApiResponse<List<SampleMeasurement>> findByPeriod(@RequestParam String tagId,
                                                             @RequestParam Instant from,
                                                             @RequestParam Instant to) {
        return ApiResponse.ok(repository.findByTagIdBetween(tagId, from, to));
    }

    /** 현재값(최신 측정) 조회 */
    @GetMapping("/{tagId}/latest")
    public ApiResponse<SampleMeasurement> findLatest(@PathVariable String tagId) {
        return ApiResponse.ok(repository.findLatest(tagId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.NOT_FOUND, "tag %s 측정값 없음".formatted(tagId))));
    }
}
