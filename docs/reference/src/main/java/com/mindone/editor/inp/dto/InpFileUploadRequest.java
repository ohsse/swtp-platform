package com.mindone.editor.inp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * INP 파일 업로드 요청 정보(JSON 파트).
 *
 * <p>멀티파트 폼 필드는 컨테이너가 플랫폼 기본 charset 으로 디코딩해 한글이 깨질 수 있어,
 * 메타데이터는 UTF-8 로 안전하게 디코딩되는 JSON 파트({@code info})로 받는다.</p>
 *
 * @param orgnlFileNm 사용자가 입력한 원본 파일명(표시용)
 */
@Schema(description = "INP 파일 업로드 요청 정보")
public record InpFileUploadRequest(
        @Schema(description = "저장할 원본 파일명(표시용)", example = "테스트관망도.inp")
        String orgnlFileNm
) {
}
