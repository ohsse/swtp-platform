package com.mo.swtp.auth;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 표준형 (Phase 6 확립 패턴의 복제).
 *
 * <p>auth-service는 Kafka를 쓰지 않으므로 PG 컨테이너만 띄운다.
 * JWT 디코더는 앱이 자기 DB 서명키로 만들기 때문에 테스트용 키 주입도 필요 없다 —
 * 발급과 검증이 같은 프로세스 안에서 짝을 이룬다.
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
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Testcontainers 2.x: PostgreSQLContainer는 비제네릭
    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb-ha:pg17")
                    .asCompatibleSubstituteFor("postgres"));
}
