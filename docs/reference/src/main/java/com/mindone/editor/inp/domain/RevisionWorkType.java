package com.mindone.editor.inp.domain;

/**
 * 리비전 작업 구분 — 해당 리비전의 내용이 "어떻게 만들어졌는가"(내용 출처)를 나타낸다.
 *
 * <p>리비전 번호(rev_no)가 순서를 나타낸다면, 이 값은 내용의 출처를 나타낸다. rev0 이라도
 * 업로드 원본이면 {@link #ORIGIN}, 편집/최적화 결과면 {@link #EDIT}/{@link #OPTIMIZE} 가 된다.</p>
 */
public enum RevisionWorkType {

    /** 최초 업로드한 원본 파일 그대로. (업로드 경로에서만 생성) */
    ORIGIN,

    /** 웹 에디터 편집 결과. (덮어쓰기/새로쓰기에서 생성) */
    EDIT,

    /** 파이썬 알고리즘 최적화 결과 모델. (파이썬 모듈이 직접 생성) */
    OPTIMIZE
}
