package com.mindone.editor.simulation.repository;

/**
 * 해석결과 조회의 지점별 원시(raw) 계측 행.
 *
 * <p>{@code TB_EPA_TAG_INFO}(위치·태그)와 {@code TB_RAWDATA}(계측값)를 조인한 결과의 한 행이다.
 * 값은 {@code TB_RAWDATA.VALUE}(varchar) 원본 문자열 그대로이며, 숫자 변환·반올림·해석값 생성은
 * 서비스 계층에서 처리한다. 태그가 없거나 해당 분 계측이 없으면 해당 값은 {@code null}.</p>
 *
 * @param name     위치명({@code LOCATION_NM})
 * @param flowRaw  유량 계측값 원본 문자열(없으면 null)
 * @param pressRaw 압력 계측값 원본 문자열(없으면 null)
 */
public record EpaMeasuredRow(String name, String flowRaw, String pressRaw) {
}
