package com.mindone.editor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan  // @ConfigurationProperties 클래스(StorageProperties 등) 자동 스캔/등록
public class EditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EditorApplication.class, args);
    }

}
