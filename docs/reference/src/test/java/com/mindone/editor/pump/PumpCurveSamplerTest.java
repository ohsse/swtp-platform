package com.mindone.editor.pump;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.pump.dto.PumpCurvePoint;
import com.mindone.editor.pump.exception.PumpCurveErrorCode;
import com.mindone.editor.pump.service.PumpCurveSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 성능곡선 샘플링 엔진({@link PumpCurveSampler}) 단위 테스트.
 *
 * <p>Spring 컨텍스트·DB 없이 컴포넌트를 직접 {@code new} 해 검증한다. 검증 포인트는
 * 표본 개수 고정, 끝점(min/max) 정확도, 회귀식 값, 구간·표본 개수 검증 예외다.</p>
 */
class PumpCurveSamplerTest {

    private final PumpCurveSampler sampler = new PumpCurveSampler();

    @Test
    @DisplayName("표본 개수만큼 점을 만들고 첫 점은 minFlow, 마지막 점은 maxFlow 로 정확히 고정한다")
    void producesExactEndpoints() {
        // P(Q) = 0·Q² + 1·Q + 0 = Q (검증 단순화를 위해 항등식)
        List<PumpCurvePoint> points = sampler.sample(0.0, 1.0, 0.0, 100.0, 2000.0, 50);

        assertThat(points).hasSize(50);
        assertThat(points.get(0).flow()).isEqualTo(100.0);
        assertThat(points.get(49).flow()).isEqualTo(2000.0);   // 누적 오차 없이 정확히 maxFlow
        // 항등식이므로 head == flow
        assertThat(points.get(0).head()).isEqualTo(100.0);
        assertThat(points.get(49).head()).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("회귀식 H(Q)=a·Q²+b·Q+c 를 정확히 계산한다")
    void evaluatesQuadratic() {
        // a=2, b=3, c=5, Q=10 → 2·100 + 3·10 + 5 = 235
        List<PumpCurvePoint> points = sampler.sample(2.0, 3.0, 5.0, 10.0, 10.0 + 1.0, 2);

        assertThat(points.get(0).flow()).isEqualTo(10.0);
        assertThat(points.get(0).head()).isEqualTo(235.0);
    }

    @Test
    @DisplayName("head(a,b,c,Q) 는 회귀식 H(Q)=a·Q²+b·Q+c 를 정확히 계산한다 (성능곡선 갱신 newCurve 산출용)")
    void headEvaluatesQuadratic() {
        // a=2, b=3, c=5, Q=10 → 2·100 + 3·10 + 5 = 235
        assertThat(PumpCurveSampler.head(2.0, 3.0, 5.0, 10.0)).isEqualTo(235.0);
        // 상수항만(c=5)일 때 임의 Q 에서 5
        assertThat(PumpCurveSampler.head(0.0, 0.0, 5.0, 12345.0)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("간격은 (max-min)/(N-1) 로 균등하다")
    void usesUniformStep() {
        List<PumpCurvePoint> points = sampler.sample(0.0, 1.0, 0.0, 0.0, 10.0, 11);

        // step = 10/10 = 1 → 0,1,2,...,10
        for (int i = 0; i < 11; i++) {
            assertThat(points.get(i).flow()).isEqualTo((double) i);
        }
    }

    @Test
    @DisplayName("minFlow >= maxFlow 면 INVALID_FLOW_RANGE")
    void rejectsInvalidRange() {
        assertThatThrownBy(() -> sampler.sample(0.0, 1.0, 0.0, 2000.0, 100.0, 50))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpCurveErrorCode.INVALID_FLOW_RANGE);
    }

    @Test
    @DisplayName("표본 개수가 MIN_SAMPLE 미만이거나 MAX_SAMPLE 초과면 INVALID_SAMPLE_COUNT")
    void rejectsInvalidSampleCount() {
        assertThatThrownBy(() -> sampler.sample(0.0, 1.0, 0.0, 0.0, 10.0, PumpCurveSampler.MIN_SAMPLE - 1))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpCurveErrorCode.INVALID_SAMPLE_COUNT);

        assertThatThrownBy(() -> sampler.sample(0.0, 1.0, 0.0, 0.0, 10.0, PumpCurveSampler.MAX_SAMPLE + 1))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpCurveErrorCode.INVALID_SAMPLE_COUNT);
    }
}
