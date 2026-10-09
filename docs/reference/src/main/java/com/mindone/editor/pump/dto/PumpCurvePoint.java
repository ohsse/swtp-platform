package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 성능곡선 위의 표본점 한 개 (유량, 양정).
 *
 * <p>펌프 성능곡선의 표준 축은 X축 유량(Q, m³/h) · Y축 양정(H, m) 이다. 양정은 회귀식
 * {@code H(Q) = quadCoef·Q² + linearCoef·Q + constCoef} 로 산출한 값이다. 유효 유량 구간 밖(외삽)
 * 또는 계수에 따라 음수가 나올 수 있으며, 백엔드는 raw 값을 그대로 반환하고 표시 정책은 프론트에 위임한다.</p>
 */
@Schema(description = "성능곡선 표본점 (유량, 양정)")
public record PumpCurvePoint(

        @Schema(description = "유량 Q (m³/h)", example = "138.78")
        double flow,

        @Schema(description = "양정 H (m) = quadCoef·flow² + linearCoef·flow + constCoef", example = "55.39")
        double head
) {

    /** 유량·양정으로 표본점을 만든다. */
    public static PumpCurvePoint of(double flow, double head) {
        return new PumpCurvePoint(flow, head);
    }
}
