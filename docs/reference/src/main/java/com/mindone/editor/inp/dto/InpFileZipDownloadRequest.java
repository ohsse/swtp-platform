package com.mindone.editor.inp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * INP 파일 ZIP 다운로드 요청.
 *
 * @param inpFileIds 다운로드할 INP 파일 ID 목록
 */
@Schema(description = "INP 파일 ZIP 다운로드 요청")
public record InpFileZipDownloadRequest(
        @Schema(description = "다운로드할 INP 파일 ID 목록") List<String> inpFileIds
) {
}
