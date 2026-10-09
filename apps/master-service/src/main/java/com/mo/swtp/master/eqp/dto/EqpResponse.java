package com.mo.swtp.master.eqp.dto;

import com.mo.swtp.master.eqp.domain.Eqp;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 설비 응답 */
@Schema(description = "설비 응답")
public record EqpResponse(

        @Schema(description = "설비ID")
        String eqpId,

        @Schema(description = "소속 시설ID")
        String fcltId,

        @Schema(description = "설비명")
        String eqpNm,

        @Schema(description = "설비타입코드")
        String eqpTypeCd,

        @Schema(description = "사용여부")
        UseYn useYn,

        @Schema(description = "정렬순서. 미지정이면 목록에서 맨 뒤로 간다", example = "1")
        Integer sortOrd,

        @Schema(description = "등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "등록자ID")
        String rgstrId
) {
    public static EqpResponse from(Eqp eqp) {
        return new EqpResponse(eqp.getEqpId(), eqp.getFcltId(), eqp.getEqpNm(), eqp.getEqpTypeCd(), eqp.getUseYn(), eqp.getSortOrd(), eqp.getRgstrDttm(), eqp.getRgstrId());
    }
}
