package com.mo.swtp.ems.ctrl.dto;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 제어그룹 등록 요청.
 *
 * <p><b>여기 걸린 것은 형식 검증이지 존재 검증이 아니다.</b> 길이와 공백 여부만 본다.
 * 편성 명세가 참조할 master 소유 키(설비ID·태그시리얼번호)의 실재 확인은 이 계층이 하지 못하며,
 * 그 장치는 아직 플랫폼에 없다(문서 01 「알려진 한계 4」).
 */
@Schema(description = "제어그룹 등록 요청")
public record CtrlGrpAddRequest(

        @Schema(description = "제어그룹ID. 서버가 만들지 않으며 호출자가 정해서 보낸다. 이미 있는 값이면 409다")
        @NotBlank
        @Size(max = ColLength.CTRL_GRP_ID)
        String ctrlGrpId,

        @Schema(description = "제어그룹명")
        @NotBlank
        @Size(max = ColLength.CTRL_GRP_NM)
        String ctrlGrpNm,

        @Schema(description = "사용여부. 생략하면 Y로 등록된다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 목록 조회에서 맨 뒤로 간다", example = "1")
        Integer sortOrd) {
}
