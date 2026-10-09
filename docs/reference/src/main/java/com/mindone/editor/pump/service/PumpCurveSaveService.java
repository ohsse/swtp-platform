package com.mindone.editor.pump.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.pump.dto.PumpCurveSaveRequest;
import com.mindone.editor.pump.exception.PumpCurveErrorCode;
import com.mindone.editor.pump.repository.PumpCurveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 펌프 성능곡선 저장(갱신) 서비스.
 *
 * <p>갱신(renewal)·추출(extraction) 미리보기로 검토한 성능곡선 회귀 계수·유효 유량 구간·평가지표를
 * {@code pump_comb_m} 한 행({@code combId})에 반영한다. 이 프로젝트에서 {@code pump_comb_m} 에 <b>쓰는 유일한</b>
 * 엔드포인트다(그 외 성능곡선 API 는 모두 조회/계산만 한다). 값은 프론트 요청 본문을 그대로 저장하며(passthrough),
 * 저장 시점에 파이썬을 재호출하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class PumpCurveSaveService {

    private final PumpCurveRepository pumpCurveRepository;

    /**
     * 펌프조합의 성능곡선(계수·유량 구간·평가지표)을 저장한다.
     *
     * @param combId  펌프조합 ID ({@code pump_comb_m.comb_id})
     * @param request 저장할 회귀 계수 + 유량 구간 + 평가지표(우선순위·데이터수 포함)
     * @return 저장된 펌프조합 ID
     * @throws RestApiException 유효 유량 구간이 잘못된 경우({@code fcMin >= fcMax}) {@link PumpCurveErrorCode#INVALID_FLOW_RANGE},
     *                          대상 조합이 없어 갱신할 행이 없으면 {@link PumpCurveErrorCode#CURVE_NOT_FOUND}
     */
    @Transactional
    public Long save(Long combId, PumpCurveSaveRequest request) {
        // 유효 유량 구간이 뒤집혀 저장되면 이후 표본 조회(sampleCurve)가 깨지므로 저장 전에 막는다.
        if (!request.hasValidFlowRange()) {
            throw new RestApiException(PumpCurveErrorCode.INVALID_FLOW_RANGE);
        }

        int updated = pumpCurveRepository.updateCurve(combId, request);
        if (updated == 0) {
            throw new RestApiException(PumpCurveErrorCode.CURVE_NOT_FOUND);
        }
        return combId;
    }
}
