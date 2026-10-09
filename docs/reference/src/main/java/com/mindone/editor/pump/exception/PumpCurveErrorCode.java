package com.mindone.editor.pump.exception;

import com.mindone.editor.common.exception.error.ErrorCode;

/**
 * 펌프 성능곡선 조회 도메인 전용 에러 코드.
 *
 * <p>본 프로젝트 규약상 모든 {@link com.mindone.editor.common.exception.RestApiException} 은
 * {@link com.mindone.editor.common.exception.advice.RestApiAdvice} 에 의해 400 으로 반환된다.
 * (설계서의 404 분기는 우리 규약과 달라 채택하지 않으며, 응답의 {@code code} 값으로 구분한다.)</p>
 */
public enum PumpCurveErrorCode implements ErrorCode {

    /** 요청한 펌프조합(comb_id)에 해당하는 성능곡선 계수가 없음. */
    CURVE_NOT_FOUND,

    /** 유효 유량 구간이 잘못됨(minFlow >= maxFlow). */
    INVALID_FLOW_RANGE,

    /** 표본 개수(sampleCount)가 허용 범위를 벗어남. */
    INVALID_SAMPLE_COUNT,

    /** 요청한 펌프조합(comb_id)이 없거나 펌프 구성(PUMP_COMB)이 비어 실측 곡선을 만들 수 없음. */
    COMBINATION_NOT_FOUND,

    /**
     * 파이썬 회귀 API 호출 실패로 성능곡선 갱신 계수를 받지 못함.
     * 연결/타임아웃/비정상 응답뿐 아니라, 회귀할 실측 데이터가 부족해 계수/지표가 {@code null} 로 내려온 경우(회귀 불가)도 포함한다.
     */
    CURVE_RENEWAL_FAILED,
    ;
}
