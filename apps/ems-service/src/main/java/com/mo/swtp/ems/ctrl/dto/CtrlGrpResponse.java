package com.mo.swtp.ems.ctrl.dto;

import java.time.LocalDateTime;

import com.mo.swtp.ems.ctrl.domain.CtrlGrp;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제어그룹 응답.
 *
 * <p><b>{@code mdf_*}를 함께 싣는다</b> — master-service의 같은 자리 응답은 등록 감사만 담지만,
 * 감사 컬럼의 목적이 추적인데 화면이 그것을 볼 경로는 응답뿐이다.
 * 제어그룹은 운전에 직결되므로 "마지막으로 누가 언제 바꿨나"가 1급 정보다.
 *
 * <p>등록 직후에는 {@code mdfDttm == rgstrDttm}이다 — 그것이 "한 번도 수정되지 않음"의 판정이다(step-14).
 */
@Schema(description = "제어그룹 응답")
public record CtrlGrpResponse(

        @Schema(description = "제어그룹ID")
        String ctrlGrpId,

        @Schema(description = "제어그룹명")
        String ctrlGrpNm,

        @Schema(description = "사용여부")
        UseYn useYn,

        @Schema(description = "정렬순서. 미지정이면 목록에서 맨 뒤로 간다", example = "1")
        Integer sortOrd,

        @Schema(description = "등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "등록자ID")
        String rgstrId,

        @Schema(description = "수정 일시. 등록 직후에는 등록 일시와 같다 — 그것이 한 번도 수정되지 않았다는 뜻이다")
        LocalDateTime mdfDttm,

        @Schema(description = "수정자ID")
        String mdfId) {

    public static CtrlGrpResponse from(CtrlGrp ctrlGrp) {
        return new CtrlGrpResponse(
                ctrlGrp.getCtrlGrpId(),
                ctrlGrp.getCtrlGrpNm(),
                ctrlGrp.getUseYn(),
                ctrlGrp.getSortOrd(),
                ctrlGrp.getRgstrDttm(),
                ctrlGrp.getRgstrId(),
                ctrlGrp.getMdfDttm(),
                ctrlGrp.getMdfId());
    }
}
