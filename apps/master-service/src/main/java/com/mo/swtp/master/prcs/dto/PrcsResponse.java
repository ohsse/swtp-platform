package com.mo.swtp.master.prcs.dto;

import com.mo.swtp.master.prcs.domain.Prcs;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 공정 응답 */
@Schema(description = "공정 응답")
public record PrcsResponse(

        @Schema(description = "공정ID. 운전모드 변경이력의 ctrlTrgtId가 이 값이다(유형이 PRCS일 때)")
        String prcsId,

        @Schema(description = "공정명")
        String prcsNm,

        @Schema(description = "공정타입코드")
        String prcsTypeCd,

        @Schema(description = "사용여부")
        UseYn useYn,

        @Schema(description = "정렬순서. 미지정이면 목록에서 맨 뒤로 간다", example = "1")
        Integer sortOrd,

        @Schema(description = "등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "등록자ID")
        String rgstrId
) {
    public static PrcsResponse from(Prcs prcs) {
        return new PrcsResponse(prcs.getPrcsId(), prcs.getPrcsNm(), prcs.getPrcsTypeCd(), prcs.getUseYn(), prcs.getSortOrd(), prcs.getRgstrDttm(), prcs.getRgstrId());
    }
}
