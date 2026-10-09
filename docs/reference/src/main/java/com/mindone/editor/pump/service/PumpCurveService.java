package com.mindone.editor.pump.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.pump.dto.PumpCurveCoef;
import com.mindone.editor.pump.dto.PumpCurvePoint;
import com.mindone.editor.pump.dto.PumpCurveResponse;
import com.mindone.editor.pump.exception.PumpCurveErrorCode;
import com.mindone.editor.pump.repository.PumpCurveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 펌프 성능곡선 표본 조회 서비스 (저장 계수 조회형 — 설계서 안 A).
 *
 * <p>{@code pump_comb_m} 에서 한 펌프조합의 회귀 계수와 유효 유량 구간(FC_MIN/FC_MAX)을 조회한 뒤,
 * 순수 샘플링 엔진({@link PumpCurveSampler})으로 (유량, 양정) 표본점을 산출한다. 데이터 조회와 계산을
 * 분리해 샘플링 로직은 DB 없이 단위 검증할 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
public class PumpCurveService {

    private final PumpCurveRepository pumpCurveRepository;
    private final PumpCurveSampler pumpCurveSampler;

    /** 성능곡선 표본 개수(고정). 구간 폭과 무관하게 항상 이 개수로 산출한다. */
    public static final int SAMPLE_COUNT = 10000;

    /**
     * 펌프조합의 성능곡선 표본점 목록을 조회한다.
     *
     * <p>유량 구간은 {@code pump_comb_m} 의 곡선 유효 구간({@code FC_MIN_VAL}~{@code FC_MAX_VAL})을 사용하고,
     * 표본 개수는 {@value #SAMPLE_COUNT} 개로 고정한다.</p>
     *
     * @param combId 펌프조합 ID ({@code pump_comb_m.comb_id})
     * @return 유량 구간(minFlow/maxFlow)과 (유량, 양정) 표본점 목록
     * @throws RestApiException 계수 미존재 시 {@link PumpCurveErrorCode#CURVE_NOT_FOUND},
     *                          유효 구간이 잘못된 경우({@code FC_MIN_VAL >= FC_MAX_VAL}) {@link PumpCurveSampler} 의 에러 코드
     */
    @Transactional(readOnly = true)
    public PumpCurveResponse sampleCurve(Long combId) {
        PumpCurveCoef coef = pumpCurveRepository.findCoefByCombId(combId)
                .orElseThrow(() -> new RestApiException(PumpCurveErrorCode.CURVE_NOT_FOUND));

        // 유량 구간은 곡선 유효 구간(FC_MIN/FC_MAX)으로 고정, 표본 개수도 고정
        List<PumpCurvePoint> data = pumpCurveSampler.sample(
                coef.quadCoef(), coef.linearCoef(), coef.constCoef(),
                coef.fcMin(), coef.fcMax(), SAMPLE_COUNT);

        // 조회한 유량 구간과 표본점을 함께 담아 반환
        return PumpCurveResponse.of(coef.fcMin(), coef.fcMax(), data);
    }
}
