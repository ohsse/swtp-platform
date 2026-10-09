package com.mindone.editor.inp.vismapping.dto;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.vismapping.domain.InpVisMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * INP 시각화 매핑 응답.
 *
 * @param mappingId     매핑 ID(대리키)
 * @param inpFileId     INP 파일 ID
 * @param junctionId    절점(junction) ID
 * @param pipeId        관로(pipe) ID
 * @param flowTagNo     유량태그번호
 * @param pressureTagNo 압력태그번호
 * @param pointNm       지점명
 * @param sortOrd       정렬순서
 * @param dispYn        표시여부
 * @param rgstDttm      등록일시
 * @param mdfDttm       수정일시
 */
@Schema(description = "INP 시각화 매핑 응답")
public record InpVisMappingResponse(
        @Schema(description = "매핑 ID(대리키)") Long mappingId,
        @Schema(description = "INP 파일 ID") String inpFileId,
        @Schema(description = "절점(junction) ID") String junctionId,
        @Schema(description = "관로(pipe) ID") String pipeId,
        @Schema(description = "유량태그번호") String flowTagNo,
        @Schema(description = "압력태그번호") String pressureTagNo,
        @Schema(description = "지점명") String pointNm,
        @Schema(description = "정렬순서") Integer sortOrd,
        @Schema(description = "표시여부") YesOrNo dispYn,
        @Schema(description = "등록일시") LocalDateTime rgstDttm,
        @Schema(description = "수정일시") LocalDateTime mdfDttm
) {

    /**
     * 매핑 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param mapping 매핑 엔티티
     */
    public static InpVisMappingResponse from(InpVisMapping mapping) {
        return new InpVisMappingResponse(
                mapping.getMappingId(),
                mapping.getInpFileId(),
                mapping.getJunctionId(),
                mapping.getPipeId(),
                mapping.getFlowTagNo(),
                mapping.getPressureTagNo(),
                mapping.getPointNm(),
                mapping.getSortOrd(),
                mapping.getDispYn(),
                mapping.getRgstDttm(),
                mapping.getMdfDttm()
        );
    }
}
