package com.mindone.editor.prediction.domain;

import lombok.Getter;

/**
 * 예측 정확도 상태 등급.
 *
 * <p>오차율(%)을 구간별 등급으로 분류한다. 화면 「알고리즘 예측 정확도 모니터링」의 '상태' 컬럼 색상에 대응한다
 * (우수→양호→주의).</p>
 *
 * <p>등급 컷오프(오차율 기준): 우수 ≤ 10%, 양호 10% 초과 ~ 30% 이하, 주의 30% 초과. 기준이 바뀌면
 * {@link #classify(Double)} 의 경계만 수정한다.</p>
 */
@Getter
public enum AccuracyGrade {

    /** 우수: 오차율 10% 이하. */
    EXCELLENT("우수"),
    /** 양호: 오차율 10% 초과 30% 이하. */
    GOOD("양호"),
    /** 주의: 오차율 30% 초과. */
    CAUTION("주의");

    /** 화면 표시용 한글 라벨. */
    private final String label;

    AccuracyGrade(String label) {
        this.label = label;
    }

    /**
     * 오차율(%)을 등급으로 분류한다.
     *
     * <p>기준: 우수(≤10%) · 양호(10% 초과 ~ 30% 이하) · 주의(30% 초과).</p>
     *
     * @param errorRate 오차율(%) (널이면 분류 불가 → 널 반환)
     * @return 등급 (오차율이 널이면 널)
     */
    public static AccuracyGrade classify(Double errorRate) {
        if (errorRate == null) {
            return null;
        }
        if (errorRate <= 10.0) {
            return EXCELLENT;
        }
        if (errorRate <= 30.0) {
            return GOOD;
        }
        return CAUTION;
    }
}
