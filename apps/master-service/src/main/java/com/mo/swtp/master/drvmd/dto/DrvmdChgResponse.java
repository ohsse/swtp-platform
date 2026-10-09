package com.mo.swtp.master.drvmd.dto;

import com.mo.swtp.common.operation.ChangeReason;
import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.common.operation.DrivenMode;
import com.mo.swtp.common.operation.IssuerService;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 운전모드 변경이력 1행 — {@code operation.drvmd_chg_h}의 전 컬럼.
 *
 * <p>리포지토리가 이 타입을 직접 반환한다. 필드가 100% 같은 도메인 record를 따로 두지 않는 것은
 * 이 슬라이스에 <b>행위도 불변식도 가진 도메인 객체가 없기</b> 때문이다
 * ({@code docs/04-운전모드-변경이력-조회.md} 결정 7).
 *
 * <p>{@code ctrlTrgtNm}이 <b>전환 시점의 명칭 스냅샷</b>인 덕분에 이 조회가 {@code ems} 스키마를
 * 읽지 않아도 된다(02 결정 4) — 소비자용 의미는 {@code @Schema}로 옮겼다.
 */
@Schema(description = "운전모드 변경이력 1행")
public record DrvmdChgResponse(

        @Schema(description = "이력ID")
        Long histId,

        @Schema(description = "제어대상 유형. ctrlTrgtId를 어느 기준정보로 해석할지 정하는 판별자다")
        ControlTargetType ctrlTrgtType,

        @Schema(description = "제어대상ID. 유형이 CTRL_GRP면 제어그룹ID, PRCS면 공정ID다")
        String ctrlTrgtId,

        @Schema(description = """
                제어대상명. 전환 시점의 스냅샷이라 이후 기준정보가 개명·해제돼도 당시 이름이 남는다 \
                — 지금의 기준정보 조회 결과와 다를 수 있으며, 그것이 오류가 아니다.""")
        String ctrlTrgtNm,

        @Schema(description = "이전 운전모드. 첫 전환이면 초기값 AI_ANLS다")
        DrivenMode bfDrvmd,

        @Schema(description = "이후 운전모드")
        DrivenMode afDrvmd,

        @Schema(description = "전환이 일어난 서비스 — 사람이 어느 화면에서 바꿨는가")
        IssuerService issSvc,

        @Schema(description = "변경사유")
        ChangeReason chgRsn,

        @Schema(description = "변경사유 비고. 코드로 표현되지 않는 서술이며, 이 응답에서 유일하게 null이 올 수 있는 필드다")
        String chgRsnRmrk,

        @Schema(description = "전환 시각")
        LocalDateTime rgstrDttm,

        @Schema(description = "모드를 바꾼 운전원 계정. 모드 전환은 언제나 사람이 하므로 항상 실제 계정이다")
        String rgstrId
) {
}
