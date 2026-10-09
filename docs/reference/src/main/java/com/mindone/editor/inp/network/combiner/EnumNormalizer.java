package com.mindone.editor.inp.network.combiner;

import java.util.Locale;
import java.util.Map;

/**
 * 콤보박스(드롭다운) 대상 <b>순수 enum 값만</b> 대문자로 정규화하는 읽기 경로 공용 헬퍼.
 *
 * <p>EPANET INP 는 키워드/enum 값의 대소문자를 무시하므로(예: {@code Closed}·{@code CLOSED} 동일 동작),
 * 원본 파일마다 표기가 제각각이다. 프론트 편집기는 콤보박스를 대문자 기준으로 구현하므로, 상세조회 응답에서
 * 이 enum 값들을 대문자로 통일해 표기 일관성을 준다(문서 {@code doc/reference/INP_콤보박스_필드_목록.md}).</p>
 *
 * <p><b>ID·자유값은 절대 건드리지 않는다</b>: EPANET 은 객체 ID(노드/링크/패턴/곡선 ID)는 대소문자를 구분하고,
 * 단위 표기({@code mg/L}) 등도 원본이 유효하다. 따라서 무차별 대문자화가 아니라 이 헬퍼로 <b>순수 enum 필드만</b>
 * 골라 정규화한다(호출부의 화이트리스트 책임).</p>
 */
final class EnumNormalizer {

    private EnumNormalizer() {
    }

    /** enum 값을 대문자로 정규화한다(null 은 그대로). */
    static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** 맵의 지정 키 값이 문자열이면 대문자로 정규화한다(값이 없거나 문자열이 아니면 무시). */
    static void upperInPlace(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String s) {
            map.put(key, s.toUpperCase(Locale.ROOT));
        }
    }
}
