package com.mindone.editor.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 파일 저장소 설정 프로퍼티.
 *
 * <p>{@code editor.storage.*} 프로퍼티를 바인딩한다.
 * 문자열로 주입된 경로는 Spring이 {@link Path} 로 자동 변환한다.</p>
 *
 * @param basePath INP 원본/백업 파일 저장 기준 경로
 */
@ConfigurationProperties(prefix = "editor.storage")
public record StorageProperties(Path basePath) {

    /**
     * 기준 경로 하위의 세부 경로를 해석한다.
     *
     * @param subPath 기준 경로에 이어 붙일 상대 경로 (예: "originals", "backups")
     * @return 기준 경로와 결합된 절대 경로
     */
    public Path resolve(String subPath) {
        return basePath.resolve(subPath);
    }
}
