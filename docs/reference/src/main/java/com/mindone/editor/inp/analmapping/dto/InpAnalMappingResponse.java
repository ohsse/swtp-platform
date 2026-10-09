package com.mindone.editor.inp.analmapping.dto;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.analmapping.domain.InpAnalMapping;
import com.mindone.editor.inp.opt.domain.DataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * INP 분석 매핑 응답.
 *
 * @param mappingId 매핑 ID(대리키)
 * @param inpFileId INP 파일 ID
 * @param nodeId    노드(node) ID
 * @param tagNo     태그번호
 * @param dataType  데이터유형(FLOW/PRESSURE)
 * @param analYn    분석여부
 * @param rgstDttm  등록일시
 * @param mdfDttm   수정일시
 */
@Schema(description = "INP 분석 매핑 응답")
public record InpAnalMappingResponse(
        @Schema(description = "매핑 ID(대리키)") Long mappingId,
        @Schema(description = "INP 파일 ID") String inpFileId,
        @Schema(description = "노드(node) ID") String nodeId,
        @Schema(description = "태그번호") String tagNo,
        @Schema(description = "데이터유형(FLOW/PRESSURE)") DataType dataType,
        @Schema(description = "분석여부") YesOrNo analYn,
        @Schema(description = "등록일시") LocalDateTime rgstDttm,
        @Schema(description = "수정일시") LocalDateTime mdfDttm
) {

    /**
     * 매핑 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param mapping 매핑 엔티티
     */
    public static InpAnalMappingResponse from(InpAnalMapping mapping) {
        return new InpAnalMappingResponse(
                mapping.getMappingId(),
                mapping.getInpFileId(),
                mapping.getNodeId(),
                mapping.getTagNo(),
                mapping.getDataType(),
                mapping.getAnalYn(),
                mapping.getRgstDttm(),
                mapping.getMdfDttm()
        );
    }
}
