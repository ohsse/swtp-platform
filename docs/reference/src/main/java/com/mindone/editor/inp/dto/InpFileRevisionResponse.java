package com.mindone.editor.inp.dto;

import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.domain.RevisionWorkType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * INP 파일 리비전(이력) 응답.
 *
 * @param revNo          리비전 번호
 * @param workType       작업 구분(ORIGIN/EDIT/OPTIMIZE)
 * @param storFileNm     저장 파일명(스토리지 실제 파일명)
 * @param downloadFileNm 다운로드 파일명(원본 파일명 확장자 앞에 {@code _r{revNo}} 접미사 — 특정 리비전 다운로드 시와 동일)
 * @param fileSz         파일 크기(byte)
 * @param current        현재 적용 리비전 여부(마스터 포인터가 이 리비전을 가리키는지)
 * @param rgstDttm       등록일시
 */
@Schema(description = "INP 파일 리비전(이력) 응답")
public record InpFileRevisionResponse(
        @Schema(description = "리비전 번호") int revNo,
        @Schema(description = "작업 구분(ORIGIN:업로드원본/EDIT:웹편집/OPTIMIZE:파이썬최적화)") RevisionWorkType workType,
        @Schema(description = "저장 파일명(스토리지 실제 파일명)") String storFileNm,
        @Schema(description = "다운로드 파일명(원본 파일명 확장자 앞에 _r{리비전번호} 접미사)") String downloadFileNm,
        @Schema(description = "파일 크기(byte)") long fileSz,
        @Schema(description = "현재 적용 리비전 여부") boolean current,
        @Schema(description = "등록일시") LocalDateTime rgstDttm
) {

    /**
     * 리비전 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param rev            리비전 엔티티
     * @param currRevNo      마스터의 현재 적용 리비전 번호(current 플래그 계산용)
     * @param downloadFileNm 다운로드 파일명(특정 리비전 다운로드와 동일한 명명 규칙으로 호출 측에서 계산해 전달)
     */
    public static InpFileRevisionResponse from(InpFileRevision rev, int currRevNo, String downloadFileNm) {
        return new InpFileRevisionResponse(
                rev.getRevNo(),
                rev.getWorkType(),
                rev.getStorFileNm(),
                downloadFileNm,
                rev.getFileSz(),
                rev.getRevNo() == currRevNo,
                rev.getRgstDttm()
        );
    }
}
