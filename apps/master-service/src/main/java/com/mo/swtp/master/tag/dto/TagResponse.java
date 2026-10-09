package com.mo.swtp.master.tag.dto;

import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.tag.domain.Tag;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 태그 응답 */
@Schema(description = "태그 응답")
public record TagResponse(

        @Schema(description = "태그시리얼번호")
        String tagSn,

        @Schema(description = "태그타입코드")
        String tagTypeCd,

        @Schema(description = "사용여부")
        UseYn useYn,

        @Schema(description = "등록 일시")
        LocalDateTime rgstrDttm,

        @Schema(description = "등록자ID")
        String rgstrId
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getTagSn(), tag.getTagTypeCd(), tag.getUseYn(), tag.getRgstrDttm(), tag.getRgstrId());
    }
}
