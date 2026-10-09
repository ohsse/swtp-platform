package com.mo.swtp.master.drvmd.dto;

import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.common.operation.DrivenMode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 특정 시각의 운전모드 — {@code GET /api/master/drvmd/mode}의 응답.
 *
 * <p>별도 현재모드 테이블({@code operation.operation_mode})을 두지 않고 이력을 SSOT로 삼는다
 * ({@code docs/04-운전모드-변경이력-조회.md} 결정 4).
 *
 * <p><b>필드명이 {@code neverChanged}였다가 {@code initialDefault}로 바뀐 이유</b>가 04 「함정 기록」 7이다
 * — 기준 시각이 과거이면 "그 시점까지 안 바뀜"일 뿐이라 이전 이름은 거짓을 말했다.
 * 소비자가 오독하지 않도록 그 경고를 {@code @Schema}에도 실었다.
 */
@Schema(description = "특정 시각의 운전모드")
public record DrvmdModeResponse(

        @Schema(description = "제어대상 유형")
        ControlTargetType ctrlTrgtType,

        @Schema(description = "제어대상ID")
        String ctrlTrgtId,

        @Schema(description = "조회 기준 시각의 운전모드")
        DrivenMode drvmd,

        @Schema(description = "그 모드가 된 시각. initialDefault가 true면 null이다")
        LocalDateTime sinceDttm,

        @Schema(description = """
                조회 기준 시각까지 이력이 없어 초기값으로 답했는가. \
                <b>true를 "한 번도 바뀐 적 없다"로 읽으면 안 된다</b> — at이 과거이면 \
                "그 시점까지" 없었다는 뜻이고 그 뒤에 전환 이력이 얼마든지 있을 수 있다. \
                at을 생략한 현재 조회에서만 두 문장이 같은 뜻이 된다.""")
        boolean initialDefault
) {

    /** 이력 행에서 만든다 — 그 행의 {@code afDrvmd}가 곧 그 시점의 모드다. */
    public static DrvmdModeResponse from(DrvmdChgResponse history) {
        return new DrvmdModeResponse(
                history.ctrlTrgtType(), history.ctrlTrgtId(),
                history.afDrvmd(), history.rgstrDttm(), false);
    }

    /** 기준 시각까지 이력이 없다 = 그 시점의 모드는 초기값 {@link DrivenMode#AI_ANLS}다. */
    public static DrvmdModeResponse initial(ControlTargetType ctrlTrgtType, String ctrlTrgtId) {
        return new DrvmdModeResponse(ctrlTrgtType, ctrlTrgtId, DrivenMode.AI_ANLS, null, true);
    }
}
