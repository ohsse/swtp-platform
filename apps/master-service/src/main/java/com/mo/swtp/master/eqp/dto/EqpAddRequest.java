package com.mo.swtp.master.eqp.dto;

import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 설비 등록 요청.
 *
 * <p>여기 걸린 것은 형식 검증이지 존재 검증이 아니다 — {@code fcltId}가 실재하는 시설인지는 보지 않는다.
 */
@Schema(description = "설비 등록 요청")
public record EqpAddRequest(

        @Schema(description = "설비ID. 서버가 만들지 않으며 호출자가 정해서 보낸다")
        @NotBlank
        @Size(max = ColLength.EQP_ID)
        String eqpId,

        @Schema(description = "소속 시설ID. 실재하는 시설인지는 검사하지 않는다 — 없는 값으로도 저장된다")
        @NotBlank
        @Size(max = ColLength.FCLT_ID)
        String fcltId,

        @Schema(description = "설비명")
        @NotBlank
        @Size(max = ColLength.EQP_NM)
        String eqpNm,

        @Schema(description = "설비타입코드")
        @NotBlank
        @Size(max = ColLength.EQP_TYPE_CD)
        String eqpTypeCd,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 목록 조회에서 맨 뒤로 간다", example = "1")
        Integer sortOrd) {}
