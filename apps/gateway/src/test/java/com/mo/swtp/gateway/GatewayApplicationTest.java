package com.mo.swtp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 앱 골격 스모크 테스트 — 컨텍스트 로드(정적 라우팅·CORS·JWT 필터 배선 포함)만 검증 */
@SpringBootTest(properties = {
        // 개발자 PC에 config-server가 떠 있으면 optional import가 원격 설정을 끌어와
        // 테스트 결과가 로컬 환경 상태에 좌우된다 — 테스트는 항상 자기 설정만으로 돈다.
        "spring.config.import="
})
class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
