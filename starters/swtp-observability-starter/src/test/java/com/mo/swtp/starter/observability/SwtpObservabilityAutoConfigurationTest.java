package com.mo.swtp.starter.observability;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SwtpObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwtpObservabilityAutoConfiguration.class));

    @Test
    @DisplayName("자동구성이 마커 빈을 등록한다")
    void autoConfigurationRegistersMarkerBean() {
        runner.run(context -> assertThat(context).hasSingleBean(SwtpObservabilityStarterMarker.class));
    }

    @Test
    @DisplayName("AutoConfiguration.imports 파일에 자동구성 클래스가 등록되어 있다")
    void importsFileContainsAutoConfiguration() throws Exception {
        // imports 파일의 오타(패키지/클래스명 불일치)를 조기에 잡는다
        var resource = getClass().getClassLoader()
                .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(resource).isNotNull();
        assertThat(Files.readString(Path.of(resource.toURI())))
                .contains(SwtpObservabilityAutoConfiguration.class.getName());
    }
}
