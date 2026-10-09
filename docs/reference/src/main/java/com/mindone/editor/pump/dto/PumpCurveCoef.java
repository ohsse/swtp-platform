package com.mindone.editor.pump.dto;

/**
 * {@code pump_comb_m} 한 행에서 읽어온 성능곡선 회귀 계수 + 유효 유량 구간.
 *
 * <p>레거시 컬럼명(add/mul/sqrt_mul)은 실제 수학적 역할(2차항/1차항/상수항)과 일치하지 않아,
 * 여기서는 역할이 드러나는 이름으로 재명명해 보관한다. (설계서 §6·§8 재명명 방침)</p>
 *
 * <ul>
 *   <li>{@code quadCoef}  = {@code P_ADD_VAL}      → 2차항 계수 a</li>
 *   <li>{@code linearCoef} = {@code P_MUL_VAL}     → 1차항 계수 b</li>
 *   <li>{@code constCoef} = {@code P_SQRT_MUL_VAL} → 상수항 c</li>
 *   <li>{@code fcMin}/{@code fcMax} = {@code FC_MIN_VAL}/{@code FC_MAX_VAL} → 곡선 유효 유량 구간</li>
 * </ul>
 *
 * <p>레거시 {@code CS_OP}/{@code SS_OP}(연산자) 컬럼은 {@code pressureCalValue} 에서 실제로 사용되지 않으므로
 * 읽지 않는다(레거시 {@code DrvnConfig.java:4944} 확인).</p>
 */
public record PumpCurveCoef(
        Long combId,
        String pumpComb,
        double quadCoef,
        double linearCoef,
        double constCoef,
        double fcMin,
        double fcMax
) {
}
