package com.mindone.editor.python.client;

import com.mindone.editor.python.config.PythonDataProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 파이썬 데이터 조회/계산 모듈(개발 tenant 30093)을 <b>동기 호출</b>하는 클라이언트.
 *
 * <p>최적화 클라이언트({@link com.mindone.editor.inp.opt.client.PythonOptClient})와 달리,
 * 요청-응답을 동기로 주고받아 계산 결과(JSON)를 즉시 받아 사용한다.
 * 구체 엔드포인트가 늘어나면 아래 범용 헬퍼({@link #get}/{@link #post})를 감싸는
 * 의미 있는 메서드를 추가한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PythonDataClient {

    private static final Logger log = LoggerFactory.getLogger(PythonDataClient.class);

    private final RestClient pythonDataRestClient;
    private final PythonDataProperties props;

    /**
     * 지정 경로로 GET 요청을 보내고 응답 본문을 원하는 타입으로 역직렬화한다.
     *
     * @param path         호출 경로(예: {@code "/calc/abc"}). base-url 뒤에 붙는다.
     * @param responseType 응답 매핑 타입(제네릭은 {@link ParameterizedTypeReference} 사용)
     * @return 역직렬화된 응답 본문
     * @throws org.springframework.web.client.RestClientException 연결 실패/타임아웃/비정상(4xx·5xx) 응답 시
     */
    public <T> T get(String path, ParameterizedTypeReference<T> responseType) {
        log.debug("파이썬 데이터 API GET 호출. base-url={}, path={}", props.baseUrl(), path);
        return pythonDataRestClient.get()
                .uri(path)
                .retrieve()
                .body(responseType);
    }

    /**
     * 지정 경로로 JSON 본문을 POST 하고 응답 본문을 원하는 타입으로 역직렬화한다.
     *
     * @param path         호출 경로(예: {@code "/calc"}). base-url 뒤에 붙는다.
     * @param body         요청 본문(JSON 직렬화 대상)
     * @param responseType 응답 매핑 타입(제네릭은 {@link ParameterizedTypeReference} 사용)
     * @return 역직렬화된 응답 본문
     * @throws org.springframework.web.client.RestClientException 연결 실패/타임아웃/비정상(4xx·5xx) 응답 시
     */
    public <T> T post(String path, Object body, ParameterizedTypeReference<T> responseType) {
        log.debug("파이썬 데이터 API POST 호출. base-url={}, path={}", props.baseUrl(), path);
        return pythonDataRestClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }
}
