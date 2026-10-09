package com.mindone.editor.pump.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.pump.dto.PumpActualCurvePoint;
import com.mindone.editor.pump.dto.PumpCombInfo;
import com.mindone.editor.pump.dto.PumpCurveAutoResult;
import com.mindone.editor.pump.dto.PumpCurveManualPythonRequest;
import com.mindone.editor.pump.dto.PumpCurveManualRequest;
import com.mindone.editor.pump.dto.PumpCurvePoint;
import com.mindone.editor.pump.dto.PumpCurveRenewResponse;
import com.mindone.editor.pump.exception.PumpCurveErrorCode;
import com.mindone.editor.pump.repository.PumpActualCurveRepository;
import com.mindone.editor.python.client.PythonDataClient;
import com.mindone.editor.python.dto.PythonDataEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 펌프 성능곡선 추출(수동) 서비스.
 *
 * <p>사용자가 차트에 직접 입력한 (유량, 양정) 점을 회귀 입력으로 파이썬에 전달해 새 회귀 계수를 받고, 같은 기간
 * 실측 유량에 그 회귀식을 적용해 새 곡선을 만든다. 실측값만으로 회귀하는 갱신({@link PumpCurveRenewService})과 달리
 * <b>회귀 입력이 사용자 입력 점</b>이라는 점만 다르며, 조회/계산만 하고 {@code pump_comb_m} 갱신은 하지 않는다.</p>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>{@code pump_comb_m} 에서 조합 메타(PUMP_COMB 문자열·우선순위)를 읽어 존재·구성을 확인한다.</li>
 *   <li>실측 곡선: {@code TB_RAWDATA} 에서 분별 (유량 합, 양정)을 집계한다({@link PumpActualCurveRepository}).</li>
 *   <li>파이썬 회귀 API({@code POST /pump-curve/manual}, 본문에 사용자 입력 점)로 새 계수·평가지표를 받는다.</li>
 *   <li>새 회귀식 {@code H(Q)=a·Q²+b·Q+c} 를 실측 유량에 1:1 적용해 새 곡선을 만든다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PumpCurveManualService {

    private static final Logger log = LoggerFactory.getLogger(PumpCurveManualService.class);

    private final PumpActualCurveRepository pumpActualCurveRepository;
    private final PythonDataClient pythonDataClient;

    /** 파이썬 날짜 파라미터 포맷(yyyy-MM-dd). */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 파이썬 회귀 API 경로: {@code POST /pump-curve/manual}. */
    private static final String MANUAL_CURVE_PATH = "/pump-curve/manual";

    /**
     * 사용자 입력 점 기반 성능곡선 추출 결과(실측 곡선 + 새 회귀 곡선 + 평가지표)를 산출한다.
     *
     * <p>조회기간은 <b>날짜 단위</b>로 받는다. 시작일은 그날 00:00:00 부터, 종료일은 그날 23:59:59.999999999 까지
     * 포함한다(파이썬에는 날짜 그대로 전달).</p>
     *
     * @param combId  펌프조합 ID ({@code pump_comb_m.comb_id})
     * @param request 추출 요청(조회기간 + 사용자 입력 점)
     * @return 실측 곡선·새 회귀 곡선·평균오차·전력 단위·우선순위
     * @throws RestApiException 입력 점이 비었거나 기간 역전 시 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          조합이 없거나 펌프 구성이 비면 {@link PumpCurveErrorCode#COMBINATION_NOT_FOUND},
     *                          파이썬 회귀 API 실패 시 {@link PumpCurveErrorCode#CURVE_RENEWAL_FAILED}
     */
    @Transactional(readOnly = true)
    public PumpCurveRenewResponse extract(Long combId, PumpCurveManualRequest request) {
        if (request == null || request.hasNoPoints()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }

        LocalDate today = LocalDate.now();
        LocalDate fromDate = Objects.requireNonNullElse(request.from(), today);
        LocalDate toDate = Objects.requireNonNullElse(request.to(), today);
        LocalDateTime f = fromDate.atStartOfDay();
        LocalDateTime t = toDate.atTime(LocalTime.MAX);   // 종료일 당일 끝까지 포함
        if (t.isBefore(f)) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }

        // 조합 메타(PUMP_COMB + 우선순위) — 없거나 펌프 구성이 비면 명확한 에러로 구분
        PumpCombInfo info = pumpActualCurveRepository.findCombInfo(combId)
                .filter(i -> !i.isBlankComb())
                .orElseThrow(() -> new RestApiException(PumpCurveErrorCode.COMBINATION_NOT_FOUND));

        // 1) 실측 곡선 (기간 중 분별 유량 합·양정)
        List<PumpActualCurvePoint> currCurve = pumpActualCurveRepository.findActualCurve(combId, f, t);

        // 2) 파이썬 회귀 API 호출(사용자 입력 점 → 새 계수·평가지표)
        PumpCurveAutoResult auto = fetchManualResult(fromDate, toDate, info.pumpComb(), request.points());

        // 3) 새 회귀식 H(Q)=a·Q²+b·Q+c 를 실측 유량에 1:1 적용 → 새 곡선
        //    a=P_ADD_VAL, b=P_MUL_VAL, c=P_SQRT_MUL_VAL (코드베이스 공통 계수 매핑)
        //    계수/상수는 BigDecimal(정밀도 보존)로 받으나, 곡선 렌더링(양정 계산)은 double 로 수행한다.
        List<PumpCurvePoint> newCurve = currCurve.stream()
                .map(p -> PumpCurvePoint.of(
                        p.flow(),
                        PumpCurveSampler.head(
                                auto.pAddVal().doubleValue(),
                                auto.pMulVal().doubleValue(),
                                auto.pSqrtMulVal().doubleValue(),
                                p.flow())))
                .toList();

        return PumpCurveRenewResponse.of(currCurve, newCurve, auto, info.priority());
    }

    /**
     * 파이썬 회귀 API 를 동기 호출(사용자 입력 점을 본문으로 POST)해 새 계수·평가지표를 받는다.
     *
     * @param from     조회 시작일(파이썬 {@code startDate})
     * @param to       조회 종료일(파이썬 {@code endDate})
     * @param pumpComb 펌프조합 문자열(파이썬 {@code pumpComb}, 예: {@code "4,6,7,11"})
     * @param points   사용자 입력 점(파이썬 {@code points})
     * @return 파이썬 회귀 결과
     * @throws RestApiException 호출 실패/빈 응답 시 {@link PumpCurveErrorCode#CURVE_RENEWAL_FAILED}
     */
    private PumpCurveAutoResult fetchManualResult(LocalDate from, LocalDate to,
                                                  String pumpComb, List<PumpCurvePoint> points) {
        PumpCurveManualPythonRequest body = new PumpCurveManualPythonRequest(
                from.format(DATE), to.format(DATE), pumpComb, points);
        try {
            // 파이썬 응답은 {"result": {...}, "status": "..."} 봉투 → result 만 벗겨 사용
            PythonDataEnvelope<PumpCurveAutoResult> envelope = pythonDataClient.post(
                    MANUAL_CURVE_PATH, body, new ParameterizedTypeReference<PythonDataEnvelope<PumpCurveAutoResult>>() {});
            PumpCurveAutoResult auto = (envelope == null) ? null : envelope.result();
            if (auto == null) {
                throw new RestApiException(PumpCurveErrorCode.CURVE_RENEWAL_FAILED);
            }
            if (!auto.hasAllValues()) {
                log.warn("파이썬 성능곡선 수동 회귀 불가(계수/지표 null). path={}, result={}", MANUAL_CURVE_PATH, auto);
                throw new RestApiException(PumpCurveErrorCode.CURVE_RENEWAL_FAILED);
            }
            return auto;
        } catch (RestClientException e) {
            log.warn("파이썬 성능곡선 수동 회귀 API 호출 실패. path={}", MANUAL_CURVE_PATH, e);
            throw new RestApiException(PumpCurveErrorCode.CURVE_RENEWAL_FAILED);
        }
    }
}
