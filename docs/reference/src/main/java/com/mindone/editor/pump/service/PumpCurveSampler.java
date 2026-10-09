package com.mindone.editor.pump.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.pump.dto.PumpCurvePoint;
import com.mindone.editor.pump.exception.PumpCurveErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 성능곡선 표본 산출 엔진 (순수 계산, Spring/DB 무의존).
 *
 * <p>성능곡선 회귀식 {@code H(Q) = quadCoef·Q² + linearCoef·Q + constCoef} (Q: 유량 m³/h, H: 양정 m)을
 * 유량 구간 {@code [minFlow, maxFlow]} 에서 {@code sampleCount} 개의 균등 표본점으로 산출한다.
 * 계수 출처(DB 조회/직접 전달)와 무관하게 재사용된다.</p>
 *
 * <h3>샘플링 방식 (설계서 §5 — 간격 고정 → 표본 개수 고정 개선)</h3>
 * <ul>
 *   <li>{@code step = (maxFlow - minFlow) / (N - 1)} — 구간 폭과 무관하게 항상 N 점(곡선 매끄러움 일정).</li>
 *   <li>마지막 점은 부동소수 누적 오차 없이 정확히 {@code maxFlow} 로 고정한다.</li>
 * </ul>
 *
 * <p>정밀도: 내부 계산은 {@code double}. 반올림 등 정밀도 손실을 백엔드에서 하지 않고 표시 자릿수는 프론트에 위임한다.</p>
 */
@Component
public class PumpCurveSampler {

    /** 표본 개수 하한(직선 두 점). */
    public static final int MIN_SAMPLE = 0;
    /** 표본 개수 상한(과도한 응답 방지). */
    public static final int MAX_SAMPLE = 50000;

    /**
     * 회귀식을 유량 구간에서 N 개 표본점으로 산출한다.
     *
     * @param quadCoef    2차항 계수 a
     * @param linearCoef  1차항 계수 b
     * @param constCoef   상수항 c
     * @param minFlow     구간 시작 유량
     * @param maxFlow     구간 끝 유량
     * @param sampleCount 표본 개수 N ({@value #MIN_SAMPLE}~{@value #MAX_SAMPLE})
     * @return 유량 오름차순 (유량, 양정) 표본점 N 개 (첫 점 minFlow, 마지막 점 maxFlow 정확 고정)
     * @throws RestApiException 구간 역전 시 {@link PumpCurveErrorCode#INVALID_FLOW_RANGE},
     *                          표본 개수 범위 위반 시 {@link PumpCurveErrorCode#INVALID_SAMPLE_COUNT}
     */
    public List<PumpCurvePoint> sample(double quadCoef, double linearCoef, double constCoef,
                                       double minFlow, double maxFlow, int sampleCount) {
        if (minFlow >= maxFlow) {
            throw new RestApiException(PumpCurveErrorCode.INVALID_FLOW_RANGE);
        }
        if (sampleCount < MIN_SAMPLE || sampleCount > MAX_SAMPLE) {
            throw new RestApiException(PumpCurveErrorCode.INVALID_SAMPLE_COUNT);
        }

        double step = (maxFlow - minFlow) / (sampleCount - 1);
        List<PumpCurvePoint> points = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            // 마지막 점은 누적 오차 없이 정확히 maxFlow 로 고정
            double flow = (i < sampleCount - 1) ? minFlow + i * step : maxFlow;
            points.add(PumpCurvePoint.of(flow, head(quadCoef, linearCoef, constCoef, flow)));
        }
        return points;
    }

    /**
     * 회귀식 {@code H(Q) = a·Q² + b·Q + c} 의 한 유량에 대한 양정을 계산한다(곡선 전체의 단일 수식 출처).
     *
     * <p>구간 샘플링({@link #sample}) 외에, 임의의 유량 점들(예: 실측 유량)에 대해 곡선 양정을 직접 평가할 때
     * 재사용한다. 검증·표본 개수 개념이 없는 순수 다항식 평가다.</p>
     *
     * @param quadCoef   2차항 계수 a
     * @param linearCoef 1차항 계수 b
     * @param constCoef  상수항 c
     * @param flow       유량 Q
     * @return 양정 H = a·Q² + b·Q + c
     */
    public static double head(double quadCoef, double linearCoef, double constCoef, double flow) {
        return quadCoef * flow * flow + linearCoef * flow + constCoef;
    }
}
