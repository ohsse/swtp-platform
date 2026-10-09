package com.mindone.editor.pump.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 펌프 성능곡선 추출(수동) 요청.
 *
 * <p>사용자가 차트 위에 직접 입력한 (유량, 양정) 점들({@code points})을 근거로, 파이썬이 회귀분석한 계수로
 * 성능곡선을 산출하기 위한 요청이다. 자동 갱신({@code .../performance-curve/renewal})이 실측값만으로 회귀하는 것과 달리,
 * 추출은 <b>사용자 입력 점</b>을 회귀 입력으로 파이썬에 전달한다(파이썬 {@code POST /pump-curve/manual}).</p>
 *
 * <p>조회기간({@code from}~{@code to})은 새 회귀 곡선(newCurve)을 그릴 유량 축을 정하는 데 쓴다. 같은 기간
 * 실측 유량을 가져와 그 유량들에 새 회귀식을 적용하므로, 추출 결과를 실제 운전 구간 위에 겹쳐 볼 수 있다.</p>
 *
 * @param from   조회 시작일(yyyy-MM-dd, 미지정 시 오늘) — 그날 00:00:00 부터
 * @param to     조회 종료일(yyyy-MM-dd, 미지정 시 오늘) — 그날 23:59:59.999999999 까지
 * @param points 사용자가 입력한 (유량, 양정) 점 목록 — 파이썬 회귀 입력
 */
@Schema(description = "펌프 성능곡선 추출(수동) 요청 (사용자 입력 점 → 파이썬 회귀)")
public record PumpCurveManualRequest(

        @Schema(description = "조회 시작일 (yyyy-MM-dd, 미지정 시 오늘) — 그날 00:00:00 부터", example = "2026-04-09")
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate from,

        @Schema(description = "조회 종료일 (yyyy-MM-dd, 미지정 시 오늘) — 그날 23:59:59 까지", example = "2026-04-09")
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate to,

        @Schema(description = "사용자가 입력한 (유량, 양정) 점 목록 — 파이썬 회귀 입력 (비어 있으면 안 됨)")
        List<PumpCurvePoint> points
) {

    /** 회귀 입력 점이 비어 있는지 여부(없거나 빈 목록). */
    public boolean hasNoPoints() {
        return points == null || points.isEmpty();
    }
}
