package com.mo.swtp.telemetry;

import java.util.List;

import com.mo.swtp.starter.security.test.SwtpTestJwt;
import com.mo.swtp.starter.security.test.SwtpTestSecurityConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 표준형 (Phase 6 확립 패턴의 복제) — 샘플 도메인 폐기 시에도 유지한다.
 *
 * <ul>
 *   <li>PG: 운영 compose와 동일한 timescale 이미지 (계획서 리스크 5 — H2 대체 불가, hypertable 검증 필수)</li>
 *   <li>Kafka: apache/kafka 네이티브 이미지용 신형 KafkaContainer</li>
 *   <li>@ServiceConnection: datasource/kafka 접속 정보를 컨테이너에서 자동 주입</li>
 * </ul>
 *
 * <p>Docker Desktop 기동 상태에서만 실행 가능하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // 개발자 PC에 config-server가 떠 있으면 optional import가 원격 설정을 끌어와
                // 테스트 결과가 로컬 환경 상태에 좌우된다 — 테스트는 항상 자기 설정만으로 돈다.
                "spring.config.import=",
                // 운영은 01-schemas.sql이 스키마를 만들지만(create-schemas: false),
                // Testcontainers에는 그 init SQL이 없으므로 Flyway가 직접 만들게 한다.
                "spring.flyway.create-schemas=true",
                // 인증 기본값이 none이므로 명시하지 않으면 "토큰 없으면 401" 방어 테스트가
                // 그냥 통과해 버린다. 운영 기본값이 아니라 이 앱의 보호 규칙이 실제로
                // 동작하는가를 보는 것이 목적이라 테스트는 검증 모드를 켠 채로 돈다.
                "swtp.auth.mode=internal"
        })
@AutoConfigureTestRestTemplate // Boot 4: TestRestTemplate 빈은 어노테이션 opt-in
// auth-service 없이 토큰을 검증한다 — 테스트 픽스처가 발급한 키로 디코더를 교체
@Import(SwtpTestSecurityConfiguration.class)
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Testcontainers 2.x: PostgreSQLContainer는 비제네릭
    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb-ha:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @LocalServerPort
    protected int port;

    @Autowired
    private TestRestTemplate authenticatedTemplate;

    /**
     * 업무 시나리오 테스트가 매 호출마다 토큰을 붙이지 않아도 되도록 기본 인증을 건다.
     * 이미 Authorization이 있으면 건드리지 않아, 개별 테스트가 다른 토큰을 쓸 수 있다.
     */
    @BeforeEach
    void authenticateByDefault() {
        authenticatedTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                request.getHeaders().setBearerAuth(SwtpTestJwt.accessToken("it-user", "it-user", "ADMIN"));
            }
            return execution.execute(request, body);
        }));
    }

    /** 인증 헤더가 전혀 없는 클라이언트 — 게이트웨이를 우회한 직접 호출을 재현한다 */
    protected ResponseEntity<String> callWithoutToken(String path) {
        return new TestRestTemplate().getForEntity("http://localhost:" + port + path, String.class);
    }
}
