package com.mindone.editor.inp.opt;

import com.mindone.editor.inp.opt.domain.DataType;
import com.mindone.editor.inp.opt.dto.OptResultComparison;
import com.mindone.editor.inp.opt.dto.ResultSnap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최적화 전/후 결과 병합 로직({@link OptResultComparison#merge}) 단위 테스트.
 *
 * <p>Spring 컨텍스트 없이 순수 정적 메서드를 직접 호출해 검증한다. 검증 포인트는
 * (지점명+데이터유형) 병합, 한쪽만 존재하는 지점 처리, 개선율 계산(상대 감소율), null 처리, 행 순서다.</p>
 */
class OptResultComparisonTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 6, 23, 0, 0);

    /** ResultSnap 생성 헬퍼(계측시간은 고정값). */
    private static ResultSnap snap(String pointNm, DataType dataType,
                                   String measureValue, String analValue, String errorRate) {
        return new ResultSnap(
                T,
                pointNm,
                dataType,
                new BigDecimal(measureValue),
                new BigDecimal(analValue),
                new BigDecimal(errorRate)
        );
    }

    @Test
    @DisplayName("같은 지점명+데이터유형은 한 행으로 병합되고 개선율은 상대 감소율로 계산된다")
    void mergesByPointNameAndDataType() {
        // 오차율 10 → 2, 개선율 = (10 - 2) / 10 * 100 = 80.0
        List<ResultSnap> prev = List.of(snap("P1", DataType.PRESSURE, "5.0", "4.0", "10"));
        List<ResultSnap> result = List.of(snap("P1", DataType.PRESSURE, "5.0", "4.8", "2"));

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged).hasSize(1);
        OptResultComparison row = merged.get(0);
        assertThat(row.pointNm()).isEqualTo("P1");
        assertThat(row.dataType()).isEqualTo(DataType.PRESSURE);
        assertThat(row.measureValue()).isEqualByComparingTo("5.0");
        assertThat(row.prevAnalValue()).isEqualByComparingTo("4.0");
        assertThat(row.resultAnalValue()).isEqualByComparingTo("4.8");
        assertThat(row.prevErrorRate()).isEqualByComparingTo("10");
        assertThat(row.resultErrorRate()).isEqualByComparingTo("2");
        assertThat(row.improvementRate()).isEqualByComparingTo("80.0");
    }

    @Test
    @DisplayName("같은 지점명이라도 데이터유형이 다르면 별개 행으로 유지된다")
    void distinguishesByDataType() {
        List<ResultSnap> prev = List.of(
                snap("P1", DataType.FLOW, "100", "90", "10"),
                snap("P1", DataType.PRESSURE, "5", "4", "20")
        );
        List<ResultSnap> result = List.of(
                snap("P1", DataType.FLOW, "100", "95", "5"),
                snap("P1", DataType.PRESSURE, "5", "4.5", "10")
        );

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged).hasSize(2);
        assertThat(merged).extracting(OptResultComparison::dataType)
                .containsExactly(DataType.FLOW, DataType.PRESSURE);
    }

    @Test
    @DisplayName("최적화 후에만 존재하는 지점도 행으로 포함되고 전 값은 null 이다")
    void includesResultOnlyPoint() {
        List<ResultSnap> prev = List.of(snap("P1", DataType.FLOW, "100", "90", "10"));
        List<ResultSnap> result = List.of(
                snap("P1", DataType.FLOW, "100", "95", "5"),
                snap("P2", DataType.FLOW, "200", "198", "1")
        );

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged).hasSize(2);
        OptResultComparison p2 = merged.get(1);
        assertThat(p2.pointNm()).isEqualTo("P2");
        assertThat(p2.prevAnalValue()).isNull();
        assertThat(p2.prevErrorRate()).isNull();
        assertThat(p2.resultAnalValue()).isEqualByComparingTo("198");
        // 전 오차율이 없으므로 개선율은 계산 불가(null)
        assertThat(p2.improvementRate()).isNull();
    }

    @Test
    @DisplayName("최적화 전에만 존재하는 지점도 행으로 포함되고 후 값은 null 이다")
    void includesPrevOnlyPoint() {
        List<ResultSnap> prev = List.of(snap("P1", DataType.FLOW, "100", "90", "10"));
        List<ResultSnap> result = List.of();

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged).hasSize(1);
        OptResultComparison row = merged.get(0);
        assertThat(row.pointNm()).isEqualTo("P1");
        assertThat(row.measureValue()).isEqualByComparingTo("100");
        assertThat(row.prevAnalValue()).isEqualByComparingTo("90");
        assertThat(row.resultAnalValue()).isNull();
        assertThat(row.resultErrorRate()).isNull();
        // 후 오차율이 없으므로 개선율은 계산 불가(null)
        assertThat(row.improvementRate()).isNull();
    }

    @Test
    @DisplayName("전 오차율이 0 이면 개선율은 계산 불가(null)다")
    void improvementRateIsNullWhenPrevErrorIsZero() {
        List<ResultSnap> prev = List.of(snap("P1", DataType.FLOW, "100", "100", "0"));
        List<ResultSnap> result = List.of(snap("P1", DataType.FLOW, "100", "100", "0"));

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).improvementRate()).isNull();
    }

    @Test
    @DisplayName("개선율은 소수 첫째자리에서 반올림된다")
    void improvementRateIsRoundedToOneDecimal() {
        // 오차율 7 → 2, 개선율 = (7 - 2) / 7 * 100 = 71.4285... → 71.4
        List<ResultSnap> prev = List.of(snap("P1", DataType.FLOW, "100", "93", "7"));
        List<ResultSnap> result = List.of(snap("P1", DataType.FLOW, "100", "98", "2"));

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged.get(0).improvementRate()).isEqualByComparingTo("71.4");
    }

    @Test
    @DisplayName("오차율이 증가하면 개선율은 음수가 된다")
    void improvementRateIsNegativeWhenErrorIncreases() {
        // 오차율 5 → 8, 개선율 = (5 - 8) / 5 * 100 = -60.0
        List<ResultSnap> prev = List.of(snap("P1", DataType.FLOW, "100", "95", "5"));
        List<ResultSnap> result = List.of(snap("P1", DataType.FLOW, "100", "92", "8"));

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        assertThat(merged.get(0).improvementRate()).isEqualByComparingTo("-60.0");
    }

    @Test
    @DisplayName("행 순서는 최적화 전 순서를 우선하고 후에만 있는 지점은 뒤에 붙는다")
    void preservesPrevOrderThenAppendsResultOnly() {
        List<ResultSnap> prev = List.of(
                snap("A", DataType.FLOW, "1", "1", "1"),
                snap("B", DataType.FLOW, "1", "1", "1")
        );
        List<ResultSnap> result = List.of(
                snap("B", DataType.FLOW, "1", "1", "1"),
                snap("C", DataType.FLOW, "1", "1", "1"),
                snap("A", DataType.FLOW, "1", "1", "1")
        );

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, Map.of());

        // 전 순서(A, B) 우선, 후에만 있는 C 는 마지막에 추가
        assertThat(merged).extracting(OptResultComparison::pointNm)
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("전/후 모두 null 또는 빈 목록이면 빈 결과를 반환한다")
    void returnsEmptyForNullOrEmptyInputs() {
        assertThat(OptResultComparison.merge(null, null, Map.of())).isEmpty();
        assertThat(OptResultComparison.merge(List.of(), List.of(), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("비교 행은 지점명으로 찾은 정렬순서대로 정렬되고, 정렬순서를 모르는 지점은 뒤로 간다")
    void sortsByPointSortOrder() {
        // 입력 순서(C, A, B)와 무관하게 정렬순서(A=0, B=1, C=2)대로 정렬되어야 한다.
        List<ResultSnap> prev = List.of(
                snap("C", DataType.FLOW, "1", "1", "1"),
                snap("A", DataType.FLOW, "1", "1", "1"),
                snap("B", DataType.FLOW, "1", "1", "1")
        );
        List<ResultSnap> result = List.of(
                snap("C", DataType.FLOW, "1", "1", "1"),
                snap("A", DataType.FLOW, "1", "1", "1"),
                snap("B", DataType.FLOW, "1", "1", "1"),
                // 정렬순서 매핑에 없는 지점 → 맨 뒤로
                snap("Z", DataType.FLOW, "1", "1", "1")
        );
        Map<String, Integer> sortOrderByPointNm = Map.of("A", 0, "B", 1, "C", 2);

        List<OptResultComparison> merged = OptResultComparison.merge(prev, result, sortOrderByPointNm);

        assertThat(merged).extracting(OptResultComparison::pointNm)
                .containsExactly("A", "B", "C", "Z");
    }

    @Test
    @DisplayName("한 지점에 유량/압력이 모두 있으면 지점별로 유량 → 압력 순으로 묶여 나온다")
    void groupsByPointThenFlowBeforePressure() {
        // 입력은 데이터유형끼리 뭉쳐 들어오지만(압력 먼저, 유량 나중) 출력은 지점별로 묶여야 한다.
        List<ResultSnap> prev = List.of(
                snap("고산분기", DataType.PRESSURE, "1", "1", "1"),
                snap("A분기", DataType.PRESSURE, "1", "1", "1"),
                snap("고산분기", DataType.FLOW, "1", "1", "1"),
                snap("A분기", DataType.FLOW, "1", "1", "1")
        );
        Map<String, Integer> sortOrderByPointNm = Map.of("고산분기", 0, "A분기", 1);

        List<OptResultComparison> merged = OptResultComparison.merge(prev, List.of(), sortOrderByPointNm);

        assertThat(merged).extracting(OptResultComparison::pointNm, OptResultComparison::dataType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("고산분기", DataType.FLOW),
                        org.assertj.core.groups.Tuple.tuple("고산분기", DataType.PRESSURE),
                        org.assertj.core.groups.Tuple.tuple("A분기", DataType.FLOW),
                        org.assertj.core.groups.Tuple.tuple("A분기", DataType.PRESSURE)
                );
    }
}
