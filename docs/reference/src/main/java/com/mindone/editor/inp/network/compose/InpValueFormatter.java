package com.mindone.editor.inp.network.compose;

import java.math.BigDecimal;

/**
 * 상세조회 응답의 값(JSON 역직렬화 결과)을 INP 토큰 문자열로 표기하는 유틸.
 *
 * <p>프론트가 보낸 값은 Jackson 역직렬화로 {@link Integer}/{@link Double}/{@link String}/{@link Boolean}
 * 등이 섞여 들어온다. INP 는 텍스트 포맷이므로 이를 사람이 읽기 좋은 숫자/문자열로 표기한다.</p>
 *
 * <ul>
 *     <li>정수형(Integer/Long 등) → {@code 200} 처럼 소수점 없이</li>
 *     <li>실수형(Double/Float/BigDecimal) → 정수값이면 {@code 30}, 아니면 {@code 7.15272} 처럼
 *         불필요한 0과 지수표기 없이(파싱 라운드트립이 안전한 최단 십진 표기)</li>
 *     <li>문자열 → 그대로(이미 표시용 값: {@code "0.0000"}, {@code "CONTINUE 10"} 등)</li>
 *     <li>{@code null} → {@code null}(호출측이 토큰 생략 여부 결정)</li>
 * </ul>
 */
public final class InpValueFormatter {

    private InpValueFormatter() {
    }

    /**
     * 값을 INP 토큰 문자열로 표기한다.
     *
     * @param value 표기할 값(숫자/문자열/불리언/{@code null})
     * @return 토큰 문자열({@code null} 이면 {@code null})
     */
    public static String format(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte
                || value instanceof java.math.BigInteger) {
            return value.toString();
        }
        if (value instanceof Number number) {
            return formatNumber(number.doubleValue());
        }
        // Boolean 등 기타 타입은 표준 문자열 표기
        return value.toString();
    }

    /** 실수를 지수표기/잉여 0 없이 표기한다(정수값이면 소수점 제거). */
    private static String formatNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return Double.toString(d);
        }
        // 정수값은 소수점 없이(200.0 → "200", -0.0 → "0")
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        // Double.toString 기반으로 BigDecimal 을 만들어 잉여 0 제거 + 평문(지수표기 없음) 표기
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }
}
