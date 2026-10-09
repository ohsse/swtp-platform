package com.mindone.editor.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 애플리케이션 기동 시 파일 저장 기준 경로가 존재하도록 보장한다.
 *
 * <p>경로가 없으면 생성하고, 생성에 실패하면 기동을 중단시킨다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageInitializer implements ApplicationRunner {

    private final StorageProperties storageProperties;

    @Override
    public void run(ApplicationArguments args) {
        Path basePath = storageProperties.basePath();
        try {
            Files.createDirectories(basePath);
            log.info("파일 저장소 기준 경로 준비 완료: {}", basePath.toAbsolutePath());
        } catch (IOException e) {
            // 저장 경로가 준비되지 않으면 파일 업로드 기능이 동작할 수 없으므로 기동을 중단한다.
            throw new UncheckedIOException("파일 저장소 기준 경로 생성 실패: " + basePath, e);
        }
    }
}
