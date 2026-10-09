package com.mo.swtp.ems.wnp.dto;

import java.time.LocalDateTime;

import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.ems.wnp.domain.Wnp;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수계통지점 응답.
 *
 * <p>{@code mdf_*}를 함께 싣는다 — 감사 컬럼의 목적이 추적인데 화면이 그것을 볼 경로는 응답뿐이다.
 * 등록 직후에는 {@code mdfDttm == rgstrDttm}이며, 그것이 "한 번도 수정되지 않음"의 판정이다(step-14).
 */
@Schema(description = "수계통지점 응답")
public record WnpResponse(

        @Schema(description = "수계통지점ID")
        String wnpId,

        @Schema(description = "수계통지점명")
        String wnpNm,

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

    public static WnpResponse from(Wnp wnp) {
        return new WnpResponse(
                wnp.getWnpId(),
                wnp.getWnpNm(),
                wnp.getUseYn(),
                wnp.getSortOrd(),
                wnp.getRgstrDttm(),
                wnp.getRgstrId(),
                wnp.getMdfDttm(),
                wnp.getMdfId());
    }
}
