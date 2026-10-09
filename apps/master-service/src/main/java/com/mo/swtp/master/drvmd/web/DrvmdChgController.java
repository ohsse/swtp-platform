package com.mo.swtp.master.drvmd.web;

import com.mo.swtp.common.api.ApiResponse;
import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.master.drvmd.dto.DrvmdChgResponse;
import com.mo.swtp.master.drvmd.dto.DrvmdModeResponse;
import com.mo.swtp.master.drvmd.service.DrvmdChgService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 운전모드 변경이력 조회 API.
 *
 * <p><b>GET만 존재한다.</b> 이력 생성 엔드포인트를 여기 두면 master가 다른 서비스의 이력을 대신
 * 기록하게 되고, 그 순간 {@code iss_svc_cd}가 호출자가 넘기는 위조 가능한 값이 된다(02 결정 1).
 * 통합 테스트가 이 클래스의 매핑에 GET 외 메서드가 없음을 단언한다.
 *
 * <p><b>{@code limit} 범위 검사를 {@code @Min}·{@code @Max}로 옮기지 않는다.</b> 스펙에는 그쪽이
 * 깔끔하지만, 제약 위반이 {@code ConstraintViolationException}이 되어 {@code GlobalExceptionHandler}가
 * 다루지 않는 경로로 빠진다 — 400이던 응답이 500이 된다. 범위는 서비스가 검사하고 스펙에는
 * {@code @Parameter} 설명으로 싣는다(05 「결정 3」).
 */
@Tag(name = "운전모드 변경이력",
        description = "제어대상(제어그룹·공정)의 운전모드 전환 이력 조회. 조회 전용이다 — 이력을 남기는 것은 모드를 바꾼 서비스의 몫이다")
@RestController
@RequestMapping("/api/master/drvmd")
@RequiredArgsConstructor
public class DrvmdChgController {

    private final DrvmdChgService drvmdChgService;

    @Operation(summary = "운전모드 변경이력 조회",
            description = """
                    전환 시각 최신순으로 반환한다.

                    결과가 비어도 빈 배열과 함께 200이다(404가 아니다) — 이력이 없는 것은 \
                    "아직 한 번도 바뀌지 않았다"는 정상 상태이지 조회 실패가 아니다.

                    400이 되는 경우가 둘이다: limit이 1~1000을 벗어나거나, from이 to보다 뒤인 경우다.""")
    @GetMapping
    public ApiResponse<List<DrvmdChgResponse>> getHistory(
            @Parameter(description = "제어대상 유형. ctrlTrgtId를 어느 기준정보로 해석할지 정하는 판별자다")
            @RequestParam ControlTargetType ctrlTrgtType,
            @Parameter(description = "제어대상ID. 유형이 CTRL_GRP면 제어그룹ID, PRCS면 공정ID다")
            @RequestParam String ctrlTrgtId,
            @Parameter(description = "조회 시작 시각(포함). ISO-8601. 생략하면 조건에서 빠진다")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "조회 종료 시각(포함). ISO-8601. 생략하면 조건에서 빠진다")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "한 번에 받을 최대 행수. 1~1000을 벗어나면 400이다 — 이력 테이블은 무한 증가하므로 무제한 조회를 열어 두지 않는다")
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(drvmdChgService.getHistory(ctrlTrgtType, ctrlTrgtId, from, to, limit));
    }

    /**
     * 사고 조사의 첫 질문("그 시각에 이 대상이 무슨 모드였나")에 직접 답하는 조회이며,
     * {@code drvmd_chg_h_i_idx01}이 이 형태를 위해 설계됐다(02).
     */
    @Operation(summary = "특정 시각의 운전모드 조회",
            description = """
                    at을 생략하면 현재 모드다 — "현재"는 별도 개념이 아니라 지금 시각을 기준으로 한 과거 조회다. \
                    현재모드 테이블을 따로 두지 않고 이력을 유일한 출처로 삼는다.

                    기준 시각까지 이력이 한 행도 없으면 초기값 AI_ANLS로 답하고 initialDefault를 true로 싣는다 \
                    (404가 아니다). 그 상태와 "누군가 AI_ANLS로 되돌렸다"는 상태는 drvmd 값이 같지만 \
                    감사 관점에서 전혀 다른 사실이라 플래그로 구분한다.""")
    @GetMapping("/mode")
    public ApiResponse<DrvmdModeResponse> getMode(
            @Parameter(description = "제어대상 유형. ctrlTrgtId를 어느 기준정보로 해석할지 정하는 판별자다")
            @RequestParam ControlTargetType ctrlTrgtType,
            @Parameter(description = "제어대상ID. 유형이 CTRL_GRP면 제어그룹ID, PRCS면 공정ID다")
            @RequestParam String ctrlTrgtId,
            @Parameter(description = "조회 기준 시각. ISO-8601. 생략하면 현재 시각이다")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return ApiResponse.ok(drvmdChgService.getMode(ctrlTrgtType, ctrlTrgtId, at));
    }
}
