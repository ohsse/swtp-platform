package com.mindone.editor.inp.dto;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.domain.RevisionWorkType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * INP 파일 메타데이터 응답.
 *
 * <p>저장 파일명({@link #storFileNm})·크기({@link #fileSz})·작업구분({@link #workType})은
 * <b>현재 적용 리비전</b> 기준 값이다.</p>
 *
 * @param inpFileId   INP 파일 ID(UUID)
 * @param orgnlFileNm 원본 파일명(사용자 입력값)
 * @param storFileNm  저장 파일명(현재 적용 리비전 기준)
 * @param fileXtns    파일 확장자
 * @param fileSz      파일 크기(byte, 현재 적용 리비전 기준)
 * @param currRevNo   현재 적용 리비전 번호
 * @param workType    작업 구분(현재 적용 리비전 기준, ORIGIN/EDIT/OPTIMIZE)
 * @param monitoringYn 모니터링 여부
 * @param rgstDttm    등록일시(최초 등록 기준)
 */
@Schema(description = "INP 파일 메타데이터 응답")
public record InpFileResponse(
        @Schema(description = "INP 파일 ID(UUID)") String inpFileId,
        @Schema(description = "원본 파일명(사용자 입력값)") String orgnlFileNm,
        @Schema(description = "저장 파일명(현재 적용 리비전 기준)") String storFileNm,
        @Schema(description = "파일 확장자") String fileXtns,
        @Schema(description = "파일 크기(byte, 현재 적용 리비전 기준)") long fileSz,
        @Schema(description = "현재 적용 리비전 번호") int currRevNo,
        @Schema(description = "작업 구분(ORIGIN:업로드원본/EDIT:웹편집/OPTIMIZE:파이썬최적화, 현재 적용 리비전 기준)")
        RevisionWorkType workType,
        @Schema(description = "모니터링 여부(Y/N)") YesOrNo monitoringYn,
        @Schema(description = "등록일시") LocalDateTime rgstDttm
) {

    /**
     * 마스터와 현재 적용 리비전으로부터 응답 DTO 를 생성한다.
     *
     * @param master     마스터 엔티티
     * @param currentRev 현재 적용 리비전(= master.currRevNo 가 가리키는 리비전)
     */
    public static InpFileResponse from(InpFile master, InpFileRevision currentRev) {
        return new InpFileResponse(
                master.getInpFileId(),
                master.getOrgnlFileNm(),
                currentRev.getStorFileNm(),
                master.getFileXtns(),
                currentRev.getFileSz(),
                master.getCurrRevNo(),
                currentRev.getWorkType(),
                master.getMonitoringYn(),
                master.getRgstDttm()
        );
    }
}
