package com.mindone.editor.pump.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실측 성능곡선 표본점 한 개 (유량, 양정).
 *
 * <p>SCADA 실측 테이블({@code TB_RAWDATA}) 한 분(TS)의 값으로 만든 점이다.</p>
 * <ul>
 *   <li><b>flow</b>: 조합이 쓰는 그룹들의 유량계 태그({@code FRI_TAG}) 실측 합(헤더 공유 태그는 중복 제거).
 *       조합이 구(그룹1)·신(그룹2) 두 그룹에 걸치면 두 그룹 FRI 합이다.</li>
 *   <li><b>head</b>: 공통 압력 태그({@code PRI_S_TAG})의 실측 원시값(단위 변환 없음). 전 펌프가 같은 태그를
 *       공유하므로 조합/그룹과 무관한 단일 값이다.</li>
 * </ul>
 */
@Schema(description = "실측 성능곡선 표본점 (유량, 양정)")
public record PumpActualCurvePoint(

        @Schema(description = "유량 Q (m³/h) — 조합이 쓰는 그룹 FRI_TAG 실측 합(공유 태그 중복 제거)", example = "20719.5")
        double flow,

        @Schema(description = "양정 H — 공통 PRI_S_TAG 실측 원시값(단위 변환 없음)", example = "5.9184")
        double head
) {

    /** 유량·양정으로 표본점을 만든다. */
    public static PumpActualCurvePoint of(double flow, double head) {
        return new PumpActualCurvePoint(flow, head);
    }
}
