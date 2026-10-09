package com.mo.swtp.master.fclt.dto;

import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 시설 등록 요청.
 *
 * <p>필드 순서가 다른 세 도메인과 다르다 — {@code sortOrd}가 {@code useYn}보다 앞이다.
 * 엔티티 생성자와 짝이라 그대로 두며, JSON은 이름으로 매핑되므로 소비자에게는 영향이 없다.
 */
@Schema(description = "시설 등록 요청")
public record FcltAddRequest(

        @Schema(description = "시설ID. 서버가 만들지 않으며 호출자가 정해서 보낸다")
        @NotBlank
        @Size(max = ColLength.FCLT_ID)
        String fcltId,

        @Schema(description = "시설명")
        @NotBlank
        @Size(max = ColLength.FCLT_NM)
        String fcltNm,

        @Schema(description = "시설타입코드")
        @NotBlank
        @Size(max = ColLength.FCLT_TYPE_CD)
        String fcltTypeCd,

        @Schema(description = "정렬순서. 생략하면 목록 조회에서 맨 뒤로 간다", example = "1")
        Integer sortOrd,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn) {}
