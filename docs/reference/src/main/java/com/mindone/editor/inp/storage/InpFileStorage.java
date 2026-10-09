package com.mindone.editor.inp.storage;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * INP 파일의 물리 저장을 담당하는 컴포넌트.
 *
 * <p>파일은 기준 경로 하위의 {@code originals} 디렉터리에 저장 파일명(UUID 기반)으로 기록된다.
 * 기준 경로는 {@link StorageProperties} 가 제공한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InpFileStorage {

    /** INP 원본 파일 저장 하위 디렉터리. */
    private static final String ORIGINAL_SUBDIR = "originals";

    private final StorageProperties storageProperties;

    /**
     * 업로드 파일을 저장 파일명으로 스토리지에 기록한다.
     *
     * @param file       업로드된 멀티파트 파일
     * @param storFileNm 저장 파일명({uuid}.{확장자})
     */
    public void store(MultipartFile file, String storFileNm) {
        Path dir = storageProperties.resolve(ORIGINAL_SUBDIR);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storFileNm));
            log.info("INP 파일 저장 완료: {}", dir.resolve(storFileNm).toAbsolutePath());
        } catch (IOException e) {
            log.error("INP 파일 저장 실패: {}", e.getMessage(), e);
            throw new RestApiException(InpFileErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * 메모리상의 바이트(편집 후 직렬화된 INP 등)를 저장 파일명으로 스토리지에 기록한다.
     *
     * <p>업로드 멀티파트 대신, "다른 이름으로 저장"처럼 서버가 직접 생성한 INP 바이트를 기록할 때 쓴다.</p>
     *
     * @param content    저장할 파일 바이트(CP949 인코딩 INP 등)
     * @param storFileNm 저장 파일명({uuid}.{확장자})
     */
    public void store(byte[] content, String storFileNm) {
        Path dir = storageProperties.resolve(ORIGINAL_SUBDIR);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(storFileNm), content);
            log.info("INP 파일 저장 완료: {}", dir.resolve(storFileNm).toAbsolutePath());
        } catch (IOException e) {
            log.error("INP 파일 저장 실패: {}", e.getMessage(), e);
            throw new RestApiException(InpFileErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * 저장 파일을 다운로드용 리소스로 로드한다.
     *
     * @param storFileNm 저장 파일명({uuid}.{확장자})
     * @return 물리 파일 리소스
     * @throws RestApiException 파일이 존재하지 않으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    public Resource loadAsResource(String storFileNm) {
        Path path = storageProperties.resolve(ORIGINAL_SUBDIR).resolve(storFileNm);
        if (!Files.exists(path)) {
            log.error("INP 파일을 찾을 수 없음: {}", path.toAbsolutePath());
            throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    /**
     * 저장 파일을 삭제한다.
     *
     * <p>레코드 삭제 트랜잭션 커밋 후 호출되는 best-effort 작업이다. 파일이 없거나 삭제에
     * 실패해도 예외를 던지지 않고(이미 레코드는 삭제됨) 로깅만 한다. 실패 시 고아 파일로 남는다.</p>
     *
     * @param storFileNm 저장 파일명({uuid}.{확장자})
     */
    public void delete(String storFileNm) {
        Path path = storageProperties.resolve(ORIGINAL_SUBDIR).resolve(storFileNm);
        try {
            if (Files.deleteIfExists(path)) {
                log.info("INP 파일 삭제 완료: {}", path.toAbsolutePath());
            } else {
                log.warn("삭제할 INP 파일이 존재하지 않음: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("INP 파일 삭제 실패(고아 파일 가능): {} - {}", path.toAbsolutePath(), e.getMessage());
        }
    }
}
