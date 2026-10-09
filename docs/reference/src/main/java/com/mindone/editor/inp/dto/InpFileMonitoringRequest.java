package com.mindone.editor.inp.dto;

import com.mindone.editor.common.domain.YesOrNo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * INP 파일 모니터링 여부 변경 요청.
 *
 * @param monitoringYn 모니터링 대상 여부(Y:모니터링 함, N:안 함)
 */
@Schema(description = "INP 파일 모니터링 여부 변경 요청")
public record InpFileMonitoringRequest(
        @Schema(description = "모니터링 대상 여부", implementation = YesOrNo.class, example = "Y") YesOrNo monitoringYn
) {
}
