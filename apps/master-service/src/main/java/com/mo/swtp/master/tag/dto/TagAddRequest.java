package com.mo.swtp.master.tag.dto;

import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 태그 등록 요청.
 *
 * <p>여기 걸린 것은 형식 검증이지 존재 검증이 아니다 — 길이와 공백 여부만 본다.
 */
@Schema(description = "태그 등록 요청")
public record TagAddRequest(

        @Schema(description = "태그시리얼번호. 서버가 만들지 않으며 호출자가 정해서 보낸다")
        @NotBlank
        @Size(max = ColLength.TAG_SN)
        String tagSn,

        @Schema(description = "태그타입코드")
        @NotBlank
        @Size(max = ColLength.TAG_TYPE_CD)
        String tagTypeCd,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn) {}
