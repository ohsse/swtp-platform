package com.mindone.editor.inp.dto;

import org.springframework.core.io.Resource;

/**
 * 다운로드 대상 (물리 리소스 + 표시용 원본 파일명).
 *
 * <p>서비스가 DB/스토리지에서 해석한 다운로드 대상을 컨트롤러/ZIP 작성기로 전달하기 위한
 * 내부 객체이며 API 응답 본문으로 직렬화되지 않는다.</p>
 *
 * @param resource    물리 파일 리소스
 * @param orgnlFileNm 표시용 원본 파일명(다운로드 파일명/ZIP 엔트리명에 사용)
 * @param fileSz      파일 크기(byte)
 */
public record InpFileDownload(Resource resource, String orgnlFileNm, long fileSz) {
}
