package com.mo.swtp.master.prcs.dto;

import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공정 등록 요청.
 *
 * <p>여기 걸린 것은 형식 검증이지 존재 검증이 아니다 — 길이와 공백 여부만 본다.
 */
@Schema(description = "공정 등록 요청")
public record PrcsAddRequest(

        @Schema(description = "공정ID. 서버가 만들지 않으며 호출자가 정해서 보낸다")
        @NotBlank
        @Size(max = ColLength.PRCS_ID)
        String prcsId,

        @Schema(description = "공정명")
        @NotBlank
        @Size(max = ColLength.PRCS_NM)
        String prcsNm,

        @Schema(description = "공정타입코드")
        @NotBlank
        @Size(max = ColLength.PRCS_TYPE_CD)
        String prcsTypeCd,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 목록 조회에서 맨 뒤로 간다", example = "1")
        Integer sortOrd) {}
