package com.mindone.editor.inp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * INP 파일 특정 리비전 다중 ZIP 다운로드 요청.
 *
 * <p>현재 적용 리비전이 아니라 각 항목이 지정한 리비전 번호의 파일을 묶어 내려받는다.
 * 같은 파일의 서로 다른 리비전을 함께 담을 수 있으며, ZIP 엔트리명은 리비전 접미사로 구분된다.</p>
 *
 * @param revisions 다운로드할 (INP 파일 ID + 리비전 번호) 목록
 */
@Schema(description = "INP 파일 특정 리비전 다중 ZIP 다운로드 요청")
public record InpFileRevisionZipDownloadRequest(
        @Schema(description = "다운로드할 (INP 파일 ID + 리비전 번호) 목록") List<InpFileRevisionRef> revisions
) {
}
