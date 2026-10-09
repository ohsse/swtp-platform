package com.mo.swtp.master;

import java.util.List;

import com.mo.swtp.starter.security.test.SwtpTestJwt;
import com.mo.swtp.starter.security.test.SwtpTestSecurityConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 표준형 — Phase 7+ 각 서비스로 복제되는 재사용 패턴.
 *
 * <ul>
 *   <li>PG: 운영 compose와 동일한 timescale 이미지 (계획서 리스크 5 — H2 대체 불가)</li>
 *   <li>Kafka: apache/kafka 네이티브 이미지용 신형 KafkaContainer (confluent용 구형과 구분)</li>
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
public abstract class AbstractIntegrationTest {

    // Testcontainers 2.x: PostgreSQLContainer는 비제네릭
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb-ha:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    /*
     * 싱글톤 컨테이너 — @Testcontainers/@Container 조합을 쓰지 않는다.
     *
     * 그 조합은 컨테이너 수명을 "테스트 클래스"에 묶는다. 이 앱의 통합 테스트 클래스가 하나였을 때는
     * 차이가 없었으나, 둘이 되는 순간 첫 클래스가 끝나며 컨테이너가 stop 되고 다음 클래스가 새 포트로
     * 다시 띄운다. 반면 Spring 컨텍스트는 설정이 같아 캐시에서 재사용되므로 옛 포트를 계속 가리키고,
     * 두 번째 클래스 전체가 "Failed to obtain JDBC Connection"으로 무너진다.
     *
     * static 블록에서 직접 start 하면 수명이 JVM에 묶여 컨텍스트 캐시와 어긋나지 않는다.
     * 정리는 Testcontainers의 Ryuk 컨테이너가 JVM 종료 시 맡는다.
     * @ServiceConnection 은 @Container 없이도 동작한다 — static 필드를 스캔해 접속 정보를 읽는다.
     */
    static {
        POSTGRES.start();
        KAFKA.start();
    }

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
