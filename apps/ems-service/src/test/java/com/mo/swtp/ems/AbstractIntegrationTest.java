package com.mo.swtp.ems;

import com.mo.swtp.starter.security.test.SwtpTestSecurityConfiguration;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 표준형.
 *
 * <p>ems-service는 Kafka를 쓰지 않으므로 PG 컨테이너만 띄운다 —
 * 띄우는 컨테이너를 의존한 스타터와 일치시킨다(apps/CLAUDE.md).
 *
 * <p>REST 표면이 생기면서 {@code RANDOM_PORT} + {@code @AutoConfigureTestRestTemplate}가 붙었다
 * (문서 02). 마이그레이션 검증 테스트는 HTTP를 쓰지 않지만 같은 컨텍스트를 공유하므로
 * 웹 서버가 함께 뜬다 — 컨텍스트를 둘로 쪼개는 비용보다 싸다.
 *
 * <p><b>{@code @Testcontainers}/{@code @Container}를 쓰지 않고 싱글턴으로 띄운다.</b>
 * 그 확장은 static 컨테이너를 <b>테스트 클래스가 끝날 때 정지</b>시키는데, Spring 컨텍스트는
 * 설정이 같으면 클래스 사이에 캐시되어 재사용된다. 그래서 두 번째 클래스는 이미 죽은 컨테이너의
 * 포트를 가리키는 DataSource를 물려받아 전부 {@code Connection refused}로 죽는다.
 *
 * <p>이 결함은 <b>통합 테스트 클래스가 앱에 하나뿐일 때는 드러나지 않는다</b> — 정지가 마지막에
 * 일어나므로 아무 일도 일어나지 않는다. ems가 클래스 3개를 갖게 되면서 처음 밟았다.
 * 컨테이너를 명시적으로 정지시키지 않아도 Ryuk가 JVM 종료 시 치운다.
 *
 * <p>Docker Desktop 기동 상태에서만 실행 가능하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
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
// auth-service 없이 토큰을 검증한다 — 테스트 픽스처가 발급한 키로 디코더를 교체
@Import(SwtpTestSecurityConfiguration.class)
@AutoConfigureTestRestTemplate // Boot 4: TestRestTemplate 빈은 어노테이션 opt-in
public abstract class AbstractIntegrationTest {

    // Testcontainers 2.x: PostgreSQLContainer는 비제네릭
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb-ha:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        // JVM 수명 동안 한 번만 뜬다. 여기서 start()를 부르지 않으면 컨테이너를 관리할 주체가 없다 —
        // @Container를 뗐으므로 JUnit 확장이 더 이상 시작해 주지 않는다.
        POSTGRES.start();
    }
}
