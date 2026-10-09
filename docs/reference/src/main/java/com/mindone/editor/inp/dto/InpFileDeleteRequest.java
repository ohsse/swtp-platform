package com.mindone.editor.inp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * INP 파일 다중 삭제 요청.
 *
 * @param inpFileIds 삭제할 INP 파일 ID 목록
 */
@Schema(description = "INP 파일 다중 삭제 요청")
public record InpFileDeleteRequest(
        @Schema(description = "삭제할 INP 파일 ID 목록") List<String> inpFileIds
) {
}
