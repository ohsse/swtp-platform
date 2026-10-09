package com.mo.swtp.ems.wnp.dto;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 수계통지점 등록 요청.
 *
 * <p>여기 걸린 것은 형식 검증이지 존재 검증이 아니다 — 길이와 공백 여부만 본다.
 */
@Schema(description = "수계통지점 등록 요청")
public record WnpAddRequest(

        @Schema(description = "수계통지점ID. 서버가 만들지 않으며 호출자가 정해서 보낸다. 이미 있는 값이면 409다")
        @NotBlank
        @Size(max = ColLength.WNP_ID)
        String wnpId,

        @Schema(description = "수계통지점명")
        @NotBlank
        @Size(max = ColLength.WNP_NM)
        String wnpNm,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 목록 조회에서 맨 뒤로 간다", example = "1")
        Integer sortOrd) {
}
