package com.mindone.editor.inp.network.parser;

import java.util.List;

/**
 * 섹션 내 데이터 한 행을 무손실로 표현한 구조.
 *
 * <p>토큰(공백/탭으로 분리된 값)과 인라인 주석({@code ;} 이후), 원본 라인, 라인 번호를 모두
 * 보존한다. 토큰만으로 타입드 모델을 만들고, 원본/주석은 향후 역직렬화(Composer)와 디버깅에 쓴다.</p>
 *
 * @param tokens        공백/탭으로 분리된 토큰 목록(주석 제외, LABELS 의 따옴표 토큰은 따옴표 제거 후 보존)
 * @param inlineComment 인라인 주석({@code ;} 이후 문자열, 없으면 {@code null})
 * @param raw           원본 라인 전체(주석 포함)
 * @param lineNo        1-based 원본 라인 번호
 */
public record SectionRow(List<String> tokens, String inlineComment, String raw, int lineNo) {

    /** 토큰 개수. */
    public int size() {
        return tokens.size();
    }

    /**
     * 지정 인덱스의 토큰을 반환한다. 범위를 벗어나면 {@code null}.
     *
     * @param index 0-based 토큰 인덱스
     * @return 토큰 문자열 또는 {@code null}
     */
    public String token(int index) {
        return (index >= 0 && index < tokens.size()) ? tokens.get(index) : null;
    }

    /** 첫 토큰(대개 객체 ID). 토큰이 없으면 {@code null}. */
    public String id() {
        return token(0);
    }
}
