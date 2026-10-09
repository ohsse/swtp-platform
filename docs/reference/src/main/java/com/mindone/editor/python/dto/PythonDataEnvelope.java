package com.mindone.editor.python.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 파이썬 데이터 API(30093) 공통 응답 봉투.
 *
 * <p>파이썬은 실제 결과를 {@code result} 로 한 번 감싸고, 처리 상태를 {@code status} 로 함께 내려준다.
 * <pre>{@code {"result": { ... }, "status": "success"}}</pre>
 * 따라서 BE 는 봉투를 벗겨 {@code result} 만 도메인 타입으로 사용한다.</p>
 *
 * @param <T>    실제 결과 타입(예: {@link com.mindone.editor.pump.dto.PumpCurveAutoResult})
 * @param result 실제 결과 본문(처리 실패/빈 결과 시 {@code null} 일 수 있음)
 * @param status 처리 상태 문자열(예: {@code "success"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonDataEnvelope<T>(T result, String status) {
}
