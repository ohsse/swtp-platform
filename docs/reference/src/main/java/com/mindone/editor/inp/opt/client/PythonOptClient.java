package com.mindone.editor.inp.opt.client;

import com.mindone.editor.inp.opt.config.PythonOptProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 파이썬 최적화 모듈에 "최적화 실행"을 요청하는 클라이언트.
 *
 * <p>이력(hist)을 공유 DB 에 저장한 뒤, 그 <b>이력 ID</b> 만 파이썬으로 전달한다.
 * 파이썬은 이 ID 로 별도 프로세스를 실행하므로 BE 는 <b>긴 최적화 처리 결과</b>는 기다리지 않는다.
 * 다만 "요청 접수" 응답까지는 동기로 받아, 접수 실패(연결 실패/타임아웃/오류 응답) 시
 * 호출 측이 이를 인지하고 프론트로 오류를 반환할 수 있게 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PythonOptClient {

    private static final Logger log = LoggerFactory.getLogger(PythonOptClient.class);

    private final RestClient pythonRestClient;
    private final PythonOptProperties props;

    /**
     * 파이썬에 최적화 실행을 요청하고 접수 응답을 받는다.
     *
     * <p>이력 행이 공유 DB 에 커밋된 뒤 호출되어야 파이썬이 해당 ID 를 조회할 수 있다.</p>
     *
     * @param histId 최적화 이력 ID
     * @throws org.springframework.web.client.RestClientException 연결 실패/타임아웃/비정상(4xx·5xx) 응답 시
     */
    public void requestOptimize(Long histId) {
        // retrieve() 는 4xx·5xx 응답이나 연결/타임아웃 오류 시 RestClientException 을 던진다.
        // 경로 변수 {histId} 는 RestClient 가 인코딩하여 치환한다. (예: GET /optimize/123)
        pythonRestClient.get()
                .uri(props.optimizePath() + "/{histId}", histId)
                .retrieve()
                .toBodilessEntity();
        log.info("파이썬 최적화 요청 접수 완료. histId={}", histId);
    }
}
