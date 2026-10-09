package com.mindone.editor.inp.storage;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.dto.InpFileDownload;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 여러 INP 파일을 ZIP 으로 묶어 출력 스트림으로 내보낸다.
 *
 * <p>ZIP 엔트리명은 사용자 표시용 원본 파일명을 사용하며, 동일 이름이 중복되면
 * {@code " (n)"} 접미사로 구분한다. {@link ZipOutputStream} 은 엔트리명을 UTF-8 로
 * 기록하므로 한글 파일명이 보존된다.</p>
 */
@Slf4j
@Component
public class InpFileZipWriter {

    /**
     * 다운로드 대상들을 ZIP 으로 묶어 스트림에 기록한다.
     *
     * @param targets 압축할 다운로드 대상 목록
     * @param out     ZIP 을 기록할 출력 스트림
     */
    public void write(List<InpFileDownload> targets, OutputStream out) {
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (InpFileDownload target : targets) {
                zos.putNextEntry(new ZipEntry(uniqueEntryName(target.orgnlFileNm(), usedNames)));
                try (InputStream in = target.resource().getInputStream()) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            // 스트리밍 도중 실패. 응답이 이미 커밋되었을 수 있어 복구는 불가하며 로깅만 한다.
            log.error("INP ZIP 생성 실패: {}", e.getMessage(), e);
            throw new RestApiException(InpFileErrorCode.FILE_DOWNLOAD_ERROR);
        }
    }

    /** ZIP 내 중복 엔트리명을 {@code " (n)"} 접미사로 유일화한다. */
    private String uniqueEntryName(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int seq = 1;
        String candidate;
        do {
            candidate = base + " (" + seq++ + ")" + ext;
        } while (!used.add(candidate));
        return candidate;
    }
}
