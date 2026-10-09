package com.mindone.editor.inp.opt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.opt.domain.DataType;
import io.swagger.v3.oas.annotations.media.Schema;

// 과거 이력의 option_snap JSON 구조가 바뀌어도 역직렬화가 깨지지 않도록 모르는 필드는 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "INP 분석 매핑 스냅샷")
public record InpAnalMappingSnap(
        @Schema(description = "매핑 ID(대리키)") Long mappingId,
        @Schema(description = "노드(node) ID") String nodeId,
        @Schema(description = "태그번호") String tagNo,
        @Schema(description = "데이터유형(FLOW/PRESSURE)") DataType dataType,
        @Schema(description = "분석여부") YesOrNo analYn
) {
}
