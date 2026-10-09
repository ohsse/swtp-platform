package com.mo.swtp.master.fclt.dto;

import com.mo.swtp.master.fclt.domain.Fclt;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 시설 응답 */
@Schema(description = "시설 응답")
public record FcltResponse(

        @Schema(description = "시설ID. 설비의 fcltId가 이 값을 가리킨다")
        String fcltId,

        @Schema(description = "시설명")
        String fcltNm,

        @Schema(description = "시설타입코드")
        String fcltTypeCd,

        @Schema(description = "정렬순서. 미지정이면 목록에서 맨 뒤로 간다", example = "1")
        Integer sortOrd,

        @Schema(description = "사용여부")
        UseYn useYn,

        @Schema(description = "등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "등록자ID")
        String rgstrId
) {
    public static FcltResponse from(Fclt fclt) {
        return new FcltResponse(fclt.getFcltId(), fclt.getFcltNm(), fclt.getFcltTypeCd(), fclt.getSortOrd(), fclt.getUseYn(), fclt.getRgstrDttm(), fclt.getRgstrId());
    }
}
