package com.mindone.editor.inp.network.service;

import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.network.compose.InpComposer;
import com.mindone.editor.inp.network.compose.InpDocument;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.writer.InpWriter;
import com.mindone.editor.inp.service.InpFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * INP 저장(쓰기) 서비스 — 편집된 상세조회 응답을 INP 파일 바이트로 직렬화한다.
 *
 * <p>상세조회의 역흐름이다: {@link InpComposer 역변환}(응답 → 전 섹션) → {@link InpWriter 직렬화}
 * (섹션 → CP949 텍스트/바이트). 상세조회({@link NetworkService})와 대칭을 이룬다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkComposeService {

    private final InpComposer inpComposer;
    private final InpWriter inpWriter;
    private final InpFileService inpFileService;

    /**
     * 저장 요청을 INP 텍스트로 직렬화한다.
     *
     * @param request 편집된 상세조회 응답
     * @return INP 텍스트
     */
    public String composeToText(NetworkSaveRequest request) {
        InpDocument document = inpComposer.compose(request);
        return inpWriter.write(document);
    }

    /**
     * 저장 요청을 INP 파일 바이트(CP949)로 직렬화한다.
     *
     * @param request 편집된 상세조회 응답
     * @return CP949 인코딩 INP 바이트
     */
    public byte[] composeToBytes(NetworkSaveRequest request) {
        InpDocument document = inpComposer.compose(request);
        byte[] bytes = inpWriter.writeBytes(document);
        log.debug("INP 직렬화 완료: {} bytes, charset={}", bytes.length, inpWriter.charsetName());
        return bytes;
    }

    /**
     * 편집 결과를 새 INP 파일로 저장한다("다른 이름으로 저장").
     *
     * <p>역변환·직렬화로 INP 바이트를 만든 뒤 {@link InpFileService#saveBytes 새 레코드 + 물리 파일}로 추가한다.
     * 원본 파일은 수정하지 않으므로, 저장 후 INP 파일 레코드와 물리 파일이 각각 1건씩 늘어난다.</p>
     *
     * @param request  편집된 상세조회 응답
     * @param fileName 저장할 새 파일명
     * @return 새로 등록된 INP 파일 메타데이터(새 ID)
     */
    public InpFileResponse saveAs(NetworkSaveRequest request, String fileName) {
        byte[] bytes = composeToBytes(request);
        return inpFileService.saveBytes(bytes, fileName);
    }

    /**
     * 편집 결과로 기존 원본 INP 파일을 덮어쓴다("원본 저장").
     *
     * <p>역변환·직렬화로 만든 INP 바이트를 {@link InpFileService#overwriteBytes 기존 레코드의 물리 파일}에
     * 다시 쓴다. 새 파일을 만드는 {@link #saveAs}와 달리 레코드/ID/파일명을 그대로 두고 내용만 교체한다.</p>
     *
     * @param inpFileId 덮어쓸 INP 파일 ID
     * @param request   편집된 상세조회 응답
     * @return 갱신된 INP 파일 메타데이터(ID 동일)
     */
    public InpFileResponse overwrite(String inpFileId, NetworkSaveRequest request) {
        byte[] bytes = composeToBytes(request);
        return inpFileService.overwriteBytes(inpFileId, bytes);
    }
}
