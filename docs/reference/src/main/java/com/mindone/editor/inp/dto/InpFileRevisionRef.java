package com.mindone.editor.inp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * INP 파일 리비전 참조 — 파일 ID + 리비전 번호로 특정 리비전을 가리킨다.
 *
 * <p>특정 리비전 다중 ZIP 다운로드처럼 (파일, 리비전) 조합을 여러 건 지정할 때 사용한다.</p>
 *
 * @param inpFileId INP 파일 ID(UUID)
 * @param revNo     리비전 번호
 */
@Schema(description = "INP 파일 리비전 참조(파일 ID + 리비전 번호)")
public record InpFileRevisionRef(
        @Schema(description = "INP 파일 ID(UUID)") String inpFileId,
        @Schema(description = "리비전 번호") int revNo
) {
}
