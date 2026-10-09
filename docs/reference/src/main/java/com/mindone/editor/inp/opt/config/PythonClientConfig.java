package com.mindone.editor.inp.opt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 파이썬 최적화 모듈 호출용 {@link RestClient} 설정.
 *
 * <p>타임아웃을 짧게 두어, 파이썬의 "요청 접수" 응답까지만 기다리고 접수 실패를 빠르게 감지한다.
 * (긴 최적화 처리는 파이썬이 별도 프로세스로 수행하므로 BE 는 기다리지 않는다.)</p>
 */
@Configuration
public class PythonClientConfig {

    /** 파이썬 API 전용 RestClient(기본 URL·타임아웃 고정). */
    @Bean
    public RestClient pythonRestClient(PythonOptProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
