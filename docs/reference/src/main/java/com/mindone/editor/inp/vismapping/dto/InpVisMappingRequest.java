package com.mindone.editor.inp.vismapping.dto;

import com.mindone.editor.common.domain.YesOrNo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * INP 시각화 매핑 등록/수정 요청.
 *
 * <p>대상 INP 파일 ID 는 경로 변수로 받으므로 본문에는 포함하지 않는다. junction/pipe ID 는 필수이며,
 * 유량태그번호/압력태그번호/지점명은 선택값이다(비어 있으면 {@code null} 로 저장).</p>
 *
 * @param junctionId    절점(junction) ID (필수)
 * @param pipeId        관로(pipe) ID (필수)
 * @param flowTagNo     유량태그번호 (선택)
 * @param pressureTagNo 압력태그번호 (선택)
 * @param pointNm       지점명 (선택)
 * @param sortOrd       정렬순서
 * @param dispYn        표시여부
 */
@Schema(description = "INP 시각화 매핑 등록/수정 요청")
public record InpVisMappingRequest(
        @Schema(description = "절점(junction) ID(INP 노드 ID)", example = "J-101") String junctionId,
        @Schema(description = "관로(pipe) ID(INP 링크 ID)", example = "P-205") String pipeId,
        @Schema(description = "유량태그번호(SCADA 태그)", example = "FT-101") String flowTagNo,
        @Schema(description = "압력태그번호(SCADA 태그)", example = "PT-101") String pressureTagNo,
        @Schema(description = "지점명(표시용)", example = "중앙로 가압장") String pointNm,
        @Schema(description = "정렬순서", example = "1") Integer sortOrd,
        @Schema(description = "표시여부", implementation = YesOrNo.class, example = "N") YesOrNo dispYn
) {
}
