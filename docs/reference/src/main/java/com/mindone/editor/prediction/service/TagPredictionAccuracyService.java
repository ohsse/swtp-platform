package com.mindone.editor.prediction.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.prediction.domain.AccuracyGrade;
import com.mindone.editor.prediction.domain.ErrorRateType;
import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.domain.TagPredEval;
import com.mindone.editor.prediction.dto.AccuracySeriesPairResponse;
import com.mindone.editor.prediction.dto.AccuracySeriesResponse;
import com.mindone.editor.prediction.dto.AccuracyStatResponse;
import com.mindone.editor.prediction.repository.TagPredEvalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 태그 예측 정확도 조회 서비스.
 *
 * <p>집계 테이블 {@code tag_pred_eval_l}(MariaDB EVENT 가 적재) 을 읽어, 화면 「알고리즘 예측 정확도 모니터링」의
 * ① 예측구간별 통계 테이블, ② 차트 시계열을 제공한다. 무거운 raw 윈도 평균 계산은 이미 EVENT 가 끝내 두었으므로
 * 여기서는 가산적 지표를 단순 합산/가공만 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TagPredictionAccuracyService {

    private final TagPredEvalRepository tagPredEvalRepository;

    /**
     * 단일 예측시간(target)의 예측구간별 정확도 통계를 조회한다.
     *
     * <p>한 시각의 결과를 예측구간(horizon)별로 비교하는 시점 조회다. 예) 태그 3857, target {@code 2026-06-26 08:00}
     * → 08:00 예측값을 10분/30분/1시간/3시간/6시간 예측구간별로 표출. 예측구간 5종은 데이터가 없어도 빈 행으로
     * 항상 분 단위 오름차순으로 반환한다.</p>
     *
     * <p>단일 시점이라 RMSE/MAE 는 해당 행의 절대오차 {@code |a-p|} 와 같고(제곱오차 sqrt = 절대오차), 표본수 n 은
     * 실측이 있으면 1, 없으면 0 이다.</p>
     *
     * @param tagNo  태그번호(필수)
     * @param target 예측시간(필수, 예측 대상 시각과 정확히 일치)
     * @param base   적중률 산출 기준 오차율(널이면 sMAPE)
     * @return 예측구간별 통계
     * @throws RestApiException 태그번호가 비거나 target 이 널이면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    @Transactional(readOnly = true)
    public List<AccuracyStatResponse> stats(String tagNo, LocalDateTime target, ErrorRateType base) {
        String tag = requireTag(tagNo);
        if (target == null) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        ErrorRateType rate = (base != null) ? base : ErrorRateType.SMAPE;

        // 평가행을 예측구간으로 인덱싱(없는 구간은 빈 행으로 채우기 위함)
        Map<PredictionDuration, TagPredEval> byDuration = new EnumMap<>(PredictionDuration.class);
        for (TagPredEval e : tagPredEvalRepository.findByTarget(tag, target, PredictionDuration.MONITORED)) {
            byDuration.put(e.getDuration(), e);
        }

        List<AccuracyStatResponse> result = new ArrayList<>();
        for (PredictionDuration d : PredictionDuration.values()) {   // 선언 순서 = 분 단위 오름차순
            result.add(toStatResponse(d, byDuration.get(d), rate));
        }
        return result;
    }

    /**
     * 차트용 시계열을 조회한다(유량태그·압력태그 각각의 시계열을 한 쌍으로 반환).
     *
     * <p>같은 예측시간 구간(from~to)으로 유량태그·압력태그를 각각 조회해, 유량 데이터셋과 압력 데이터셋을 함께 돌려준다.
     * 각 데이터셋은 실측 1개 라인 + 예측구간별 예측 라인으로 구성된다.</p>
     *
     * @param from          예측시간 시작(널이면 오늘 00:00:00)
     * @param to            예측시간 끝(널이면 오늘 23:59:59.999999999)
     * @param flowTagNo     유량태그번호(필수)
     * @param pressureTagNo 압력태그번호(필수)
     * @return 유량/압력 시계열 한 쌍
     * @throws RestApiException 태그번호가 비면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    @Transactional(readOnly = true)
    public AccuracySeriesPairResponse series(LocalDateTime from, LocalDateTime to,
                                             String flowTagNo, String pressureTagNo) {
        String flowTag = requireTag(flowTagNo);
        String pressureTag = requireTag(pressureTagNo);
        LocalDateTime f = (from != null) ? from : LocalDate.now().atStartOfDay();
        LocalDateTime t = (to != null) ? to : LocalDate.now().atTime(LocalTime.MAX);

        return new AccuracySeriesPairResponse(
                buildSeries(flowTag, f, t),
                buildSeries(pressureTag, f, t));
    }

    // ===== 내부 헬퍼 =====

    /** 단일 태그의 시계열(실측 1개 라인 + 예측구간별 예측 라인)을 만든다. */
    private AccuracySeriesResponse buildSeries(String tag, LocalDateTime from, LocalDateTime to) {
        List<TagPredEval> rows = tagPredEvalRepository.findSeries(tag, from, to, PredictionDuration.MONITORED);

        // 예측구간별로 예측 점 묶기(쿼리에서 duration·predDttm 순 정렬되어 옴)
        Map<PredictionDuration, List<AccuracySeriesResponse.PredPoint>> points = new EnumMap<>(PredictionDuration.class);
        for (TagPredEval r : rows) {
            points.computeIfAbsent(r.getDuration(), k -> new ArrayList<>())
                    .add(new AccuracySeriesResponse.PredPoint(r.getPredDttm(), r.getPredValue()));
        }

        // 실측 라인: 존재하는 예측구간 중 가장 짧은 구간의 actual_avg 를 사용(측정 곡선에 가장 근접)
        List<AccuracySeriesResponse.ActualPoint> actual = buildActualSeries(rows);

        // 예측구간 5종은 데이터가 없어도 빈 배열로 항상 포함한다.
        return new AccuracySeriesResponse(
                actual,
                points.getOrDefault(PredictionDuration.M10, List.of()),
                points.getOrDefault(PredictionDuration.M30, List.of()),
                points.getOrDefault(PredictionDuration.H1, List.of()),
                points.getOrDefault(PredictionDuration.H3, List.of()),
                points.getOrDefault(PredictionDuration.H6, List.of()));
    }

    private AccuracyStatResponse toStatResponse(PredictionDuration d, TagPredEval e, ErrorRateType rate) {
        if (e == null) {   // 해당 예측구간 예측점 없음 → 빈 행
            return new AccuracyStatResponse(d, d.getLabel(), d.getMinutes(),
                    null, null, null, null, null, null, null, null, null, 0L);
        }
        Double actual = e.getActualAvg();
        // 단일 시점이라 RMSE = sqrt(제곱오차) = 절대오차 |a-p| 와 동일하다(컬럼 형태 유지를 위해 그대로 채움).
        Double rmse = (e.getSqErr() == null) ? null : Math.sqrt(e.getSqErr());
        Double errorRate = (rate == ErrorRateType.MAPE) ? e.getApe() : e.getSape();
        Double hitRate = (errorRate == null) ? null : 100.0 - errorRate;
        AccuracyGrade grade = AccuracyGrade.classify(errorRate);   // 등급은 오차율 기준(우수≤10·양호≤30·주의>30)

        return new AccuracyStatResponse(
                d, d.getLabel(), d.getMinutes(),
                actual, e.getPredValue(),
                rmse, e.getAbsErr(), e.getApe(), e.getSape(),
                hitRate, grade, (grade == null ? null : grade.getLabel()),
                (actual == null) ? 0L : 1L);
    }

    /** 가장 짧은(분 단위 최소) 예측구간 평가행의 actual_avg 로 실측 라인을 만든다. */
    private List<AccuracySeriesResponse.ActualPoint> buildActualSeries(List<TagPredEval> rows) {
        // 존재하는 예측구간 중 가장 짧은 구간(측정 곡선에 가장 근접)을 고른다.
        PredictionDuration shortest = rows.stream()
                .map(TagPredEval::getDuration)
                .min(Comparator.comparingInt(PredictionDuration::getMinutes))
                .orElse(null);
        if (shortest == null) {
            return List.of();
        }
        // rows 는 duration·predDttm 순 정렬되어 오므로 해당 구간만 추리면 예측시간순이 유지된다.
        return rows.stream()
                .filter(r -> r.getDuration() == shortest)
                .map(r -> new AccuracySeriesResponse.ActualPoint(r.getPredDttm(), r.getActualAvg()))
                .toList();
    }

    private String requireTag(String tagNo) {
        if (tagNo == null || tagNo.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        return tagNo.trim();
    }
}
