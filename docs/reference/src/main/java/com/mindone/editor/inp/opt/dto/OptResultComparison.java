package com.mindone.editor.inp.opt.dto;

import com.mindone.editor.inp.opt.domain.DataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 최적화 전/후 결과 비교 행.
 *
 * <p>같은 지점명({@link #pointNm}) + 데이터유형({@link #dataType}) 을 가진 최적화 전
 * 결과(prevResultSnap)와 최적화 후 결과(resultSnap)를 한 레코드로 병합한 것이다.</p>
 *
 * @param pointNm         지점명
 * @param dataType        데이터유형(FLOW/PRESSURE)
 * @param measureValue    계측값
 * @param prevAnalValue   최적화 전 분석값
 * @param resultAnalValue 최적화 후 분석값
 * @param prevErrorRate   최적화 전 오차율
 * @param resultErrorRate 최적화 후 오차율
 * @param improvementRate 개선율(%, (전 오차율 - 후 오차율) / 전 오차율 * 100)
 */
@Schema(description = "최적화 전/후 결과 비교 행")
public record OptResultComparison(
        @Schema(description = "지점명") String pointNm,
        @Schema(description = "데이터유형(FLOW/PRESSURE)") DataType dataType,
        @Schema(description = "계측값") BigDecimal measureValue,
        @Schema(description = "최적화 전 분석값") BigDecimal prevAnalValue,
        @Schema(description = "최적화 후 분석값") BigDecimal resultAnalValue,
        @Schema(description = "최적화 전 오차율") BigDecimal prevErrorRate,
        @Schema(description = "최적화 후 오차율") BigDecimal resultErrorRate,
        @Schema(description = "개선율(%, (전 오차율 - 후 오차율) / 전 오차율 * 100)") BigDecimal improvementRate
) {

    /**
     * 최적화 전/후 결과 스냅샷을 (지점명 + 데이터유형) 기준으로 병합한다.
     *
     * <p>두 스냅샷 중 어느 한쪽에만 존재하는 지점도 행으로 포함한다.
     * 행 순서는 1차로 지점명으로 찾은 정렬순서({@code sortOrderByPointNm}) 오름차순,
     * 2차로 데이터유형({@code FLOW} → {@code PRESSURE}) 순이다. 따라서 한 지점에
     * 유량/압력이 모두 있으면 "지점 유량 → 지점 압력" 으로 묶여서 나온다.
     * 정렬순서를 알 수 없는 행은 뒤로 보낸다.</p>
     *
     * @param prevSnaps          최적화 전 결과 목록(없으면 빈 목록 취급)
     * @param resultSnaps        최적화 후 결과 목록(없으면 빈 목록 취급)
     * @param sortOrderByPointNm 지점명 → 정렬순서 매핑(설정 스냅샷 기준)
     * @return 정렬순서대로 정렬된 비교 행 목록
     */
    public static List<OptResultComparison> merge(
            List<ResultSnap> prevSnaps,
            List<ResultSnap> resultSnaps,
            Map<String, Integer> sortOrderByPointNm) {
        Map<String, ResultSnap> prevByKey = indexByKey(prevSnaps);
        Map<String, ResultSnap> resultByKey = indexByKey(resultSnaps);

        // 행 순서: 최적화 전 키를 먼저, 이어서 후에만 존재하는 키를 추가한다.
        Map<String, Boolean> orderedKeys = new LinkedHashMap<>();
        prevByKey.keySet().forEach(k -> orderedKeys.put(k, Boolean.TRUE));
        resultByKey.keySet().forEach(k -> orderedKeys.putIfAbsent(k, Boolean.TRUE));

        // 1차: 지점 정렬순서 오름차순(모르는 지점은 뒤로), 2차: 데이터유형(FLOW → PRESSURE).
        // 같은 지점의 유량/압력을 한 묶음으로 보여주기 위함이다.
        Comparator<OptResultComparison> bySortOrder = Comparator.comparing(
                (OptResultComparison c) -> sortOrderByPointNm.get(c.pointNm()),
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<OptResultComparison> byDataType = Comparator.comparing(
                OptResultComparison::dataType,
                Comparator.nullsLast(Comparator.naturalOrder()));

        return orderedKeys.keySet().stream()
                .map(key -> toComparison(prevByKey.get(key), resultByKey.get(key)))
                .sorted(bySortOrder.thenComparing(byDataType))
                .toList();
    }

    /** 지점명 + 데이터유형 을 키로 스냅샷을 인덱싱한다(원본 순서 보존). */
    private static Map<String, ResultSnap> indexByKey(List<ResultSnap> snaps) {
        Map<String, ResultSnap> indexed = new LinkedHashMap<>();
        if (snaps == null) {
            return indexed;
        }
        for (ResultSnap snap : snaps) {
            indexed.putIfAbsent(key(snap), snap);
        }
        return indexed;
    }

    private static String key(ResultSnap snap) {
        return snap.pointNm() + "|" + snap.dataType();
    }

    /** 전/후 스냅샷 한 쌍을 비교 행으로 변환한다(한쪽은 null 일 수 있음). */
    private static OptResultComparison toComparison(ResultSnap prev, ResultSnap result) {
        ResultSnap base = (result != null) ? result : prev;

        BigDecimal prevError = (prev != null) ? prev.errorRate() : null;
        BigDecimal resultError = (result != null) ? result.errorRate() : null;

        // 계측값은 전/후가 동일한 실측값이므로 후 → 전 순으로 존재하는 값을 사용한다.
        BigDecimal measureValue = (result != null) ? result.measureValue()
                : (prev != null ? prev.measureValue() : null);

        return new OptResultComparison(
                base.pointNm(),
                base.dataType(),
                measureValue,
                (prev != null) ? prev.analValue() : null,
                (result != null) ? result.analValue() : null,
                prevError,
                resultError,
                calcImprovementRate(prevError, resultError)
        );
    }

    /**
     * 개선율(%)을 계산한다. (전 오차율 - 후 오차율) / 전 오차율 * 100.
     *
     * <p>전/후 오차율이 없거나 전 오차율이 0 이면 계산할 수 없어 {@code null} 을 반환한다.</p>
     */
    private static BigDecimal calcImprovementRate(BigDecimal prevError, BigDecimal resultError) {
        if (prevError == null || resultError == null || prevError.signum() == 0) {
            return null;
        }
        return prevError.subtract(resultError)
                .multiply(BigDecimal.valueOf(100))
                .divide(prevError, 1, RoundingMode.HALF_UP);
    }
}
