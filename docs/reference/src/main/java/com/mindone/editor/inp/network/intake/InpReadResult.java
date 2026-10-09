package com.mindone.editor.inp.network.intake;

import java.util.List;

/**
 * INP 파일 읽기 결과.
 *
 * <p>인코딩 자동판별을 거쳐 디코딩된 텍스트 라인 목록과, 실제로 사용된 문자셋 이름을 함께 담는다.
 * 사용된 문자셋은 상세조회 응답 메타에 노출되어 한글 깨짐 디버깅에 활용된다.</p>
 *
 * @param lines       디코딩된 라인 목록(개행 제거, 원본 순서 유지)
 * @param charsetName 실제 디코딩에 사용된 문자셋 이름(예: MS949, UTF-8)
 */
public record InpReadResult(List<String> lines, String charsetName) {
}
