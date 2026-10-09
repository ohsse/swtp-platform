package com.mindone.editor.simulation.service;

import com.mindone.editor.simulation.dto.EpaAnalysisResultItem;
import com.mindone.editor.simulation.repository.EpaAnalysisResultRepository;
import com.mindone.editor.simulation.repository.EpaMeasuredRow;
import com.mindone.editor.simulation.support.AnalDateTimeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 해석결과 조회 서비스.
 *
 * <p>표시대상 위치별로 유량/압력 계측값을 조회하고, 각 계측값에 <b>5% 미만의 랜덤 오차(±)</b>를 적용한
 * 해석값(목업)을 생성해 함께 반환한다. 실제 해석엔진 결과가 아니라 프론트 표출용 모의값이며, 추후 실제 해석 API 로 교체 가능하다.</p>
 *
 * <p><b>계산</b>: 오차율 {@code rate ∈ (-5, 5)}(단위 %, 5% 미만) 를 유량·압력 각각 독립적으로 뽑아
 * {@code analVal = val × (1 + rate/100)} 로 계산한다. 매 호출마다 랜덤이라 결과가 달라진다.</p>
 * <p><b>자릿수</b>: 유량은 정수, 압력은 소수 2자리, 오차율(%)은 소수 2자리로 반올림한다.</p>
 */
@Service
@RequiredArgsConstructor
public class EpaAnalysisResultService {

    /** 오차 상한(basis point, 1/10000). 499 → 최대 ±4.99% 로 "5% 미만"을 보장한다. */
    private static final int MAX_ERROR_BP = 499;

    /** 오차율 소수 자릿수(퍼센트, 소수 2자리). */
    private static final int RATE_SCALE = 2;
    /** 유량 반올림 자릿수(정수). */
    private static final int FLOW_SCALE = 0;
    /** 압력 반올림 자릿수(소수 2자리). */
    private static final int PRESS_SCALE = 2;

    private final EpaAnalysisResultRepository repository;

    /**
     * 분석일시별 해석결과 캐시.
     *
     * <p>해석값에 매 호출 랜덤 오차가 섞여 결과가 달라지므로, 같은 분석일시는 같은 결과를 돌려주도록
     * 최초 산출 결과를 캐싱한다(목업 특성상 무기한 보관·무효화 없음).</p>
     */
    private final Map<LocalDateTime, List<EpaAnalysisResultItem>> resultCache = new ConcurrentHashMap<>();

    /**
     * 분석일시 기준으로 표시대상 위치별 해석결과(계측값 + 목업 해석값) 목록을 조회한다.
     *
     * <p>같은 분석일시로 다시 호출하면 캐시된 동일 결과를 반환한다(랜덤 오차 고정).</p>
     *
     * @param analDateTime 분석일시 문자열(형식 {@code yyyy-MM-dd HH:mm})
     * @return 위치별 해석결과 목록({@code DISPLAY_ORDER} 순)
     */
    @Transactional(readOnly = true)
    public List<EpaAnalysisResultItem> analyze(String analDateTime) {
        LocalDateTime ts = AnalDateTimeParser.parse(analDateTime);
        return resultCache.computeIfAbsent(ts, key ->
                repository.findMeasuredRows(key).stream()
                        .map(EpaAnalysisResultService::toItem)
                        .toList());
    }

    /**
     * 해당 분석일시의 해석결과가 이미 캐시에 있는지(= 지연 없이 즉시 응답 가능한지) 여부.
     *
     * @param analDateTime 분석일시 문자열(형식 {@code yyyy-MM-dd HH:mm})
     * @return 캐시에 존재하면 {@code true}
     */
    public boolean isCached(String analDateTime) {
        return resultCache.containsKey(AnalDateTimeParser.parse(analDateTime));
    }

    /** 원시 계측 행 → 해석결과 항목(유량 정수, 압력 소수 2자리, 각 계측값에 5% 미만 오차 적용). */
    private static EpaAnalysisResultItem toItem(EpaMeasuredRow row) {
        BigDecimal flowVal = round(parse(row.flowRaw()), FLOW_SCALE);
        BigDecimal pressVal = round(parse(row.pressRaw()), PRESS_SCALE);

        BigDecimal flowRate = flowVal == null ? null : randomErrorRate();
        BigDecimal pressRate = pressVal == null ? null : randomErrorRate();

        return new EpaAnalysisResultItem(
                row.name(),
                toLong(flowVal),
                toLong(applyRate(flowVal, flowRate, FLOW_SCALE)),
                flowRate,
                pressVal,
                applyRate(pressVal, pressRate, PRESS_SCALE),
                pressRate
        );
    }

    /** 계측값에 오차율(%)을 적용한 해석값 {@code val × (1 + rate/100)} 을 지정 자릿수로 반올림(둘 중 하나라도 null 이면 null). */
    private static BigDecimal applyRate(BigDecimal val, BigDecimal ratePercent, int scale) {
        if (val == null || ratePercent == null) {
            return null;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(ratePercent.movePointLeft(2));
        return val.multiply(multiplier).setScale(scale, RoundingMode.HALF_UP);
    }

    /** 계측값 원본 문자열 → BigDecimal(숫자 아니면 null). */
    private static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 지정 자릿수로 반올림(null 보존). */
    private static BigDecimal round(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    /** BigDecimal → Long(null 보존). */
    private static Long toLong(BigDecimal value) {
        return value == null ? null : value.longValueExact();
    }

    /** ±5% 미만 랜덤 오차율(-4.99 ~ 4.99, 단위 %, 소수 2자리). */
    private static BigDecimal randomErrorRate() {
        int bp = ThreadLocalRandom.current().nextInt(-MAX_ERROR_BP, MAX_ERROR_BP + 1);
        return BigDecimal.valueOf(bp).movePointLeft(RATE_SCALE);
    }
}
