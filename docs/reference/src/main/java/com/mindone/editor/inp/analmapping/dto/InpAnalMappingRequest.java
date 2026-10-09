package com.mindone.editor.inp.analmapping.dto;

import com.mindone.editor.common.domain.YesOrNo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * INP 분석 매핑 등록/수정 요청.
 *
 * <p>대상 INP 파일 ID 는 경로 변수로 받으므로 본문에는 포함하지 않는다. node ID / 태그번호는 필수다.
 * 데이터유형(FLOW/PRESSURE)은 요청으로 받지 않고 태그번호 규칙으로 서버가 결정한다
 * (태그에 {@code FRI} 포함→FLOW, {@code PRI} 포함→PRESSURE, 둘 다 없으면 오류).</p>
 *
 * @param nodeId  노드(node) ID (필수)
 * @param tagNo   태그번호 (필수, 데이터유형 판별에도 사용)
 * @param analYn  분석여부 (선택, 미지정 시 {@code Y})
 */
@Schema(description = "INP 분석 매핑 등록/수정 요청")
public record InpAnalMappingRequest(
        @Schema(description = "노드(node) ID(INP 객체 ID)", example = "J-101") String nodeId,
        @Schema(description = "태그번호(SCADA 태그, 데이터유형 판별에 사용: FRI→FLOW, PRI→PRESSURE)", example = "FRI-2001") String tagNo,
        @Schema(description = "분석여부(미지정 시 Y)", implementation = YesOrNo.class, example = "Y") YesOrNo analYn
) {
}
