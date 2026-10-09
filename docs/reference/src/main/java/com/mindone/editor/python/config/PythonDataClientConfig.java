package com.mindone.editor.python.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 파이썬 데이터 조회/계산 모듈 호출용 {@link RestClient} 설정.
 *
 * <p>최적화용 {@code pythonRestClient}(짧은 타임아웃, 접수만 확인)와 달리,
 * 이 클라이언트는 동기 호출로 계산 결과를 받아오므로 읽기 타임아웃을 길게 둔다.
 * Bean 이름을 {@code pythonDataRestClient} 로 구분해 주입 충돌을 피한다.</p>
 */
@Configuration
public class PythonDataClientConfig {

    /** 파이썬 데이터 API 전용 RestClient(기본 URL·타임아웃 고정). */
    @Bean
    public RestClient pythonDataRestClient(PythonDataProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
