# starters/ — swtp-* 자동구성 스타터

`swtp.spring-library` 규약(java-library + Boot BOM `platform()`). Boot 플러그인은 적용하지 않는다.

| 스타터 | 책임 |
|---|---|
| web | `ErrorCode`/`GlobalExceptionHandler`, MDC 필터, 공통 OpenAPI 명세 |
| security | JWT **검증 전담**(서블릿), `SwtpPrincipal`/`@CurrentUser`, 통합테스트용 testFixtures |
| persistence | JDBC/Flyway/p6spy, `BaseEntity`·`BaseCreatedEntity`, JPA Auditing |
| kafka | 직렬화 기본값, `EventEnvelope`/퍼블리셔/파서 |
| observability | 로깅(콘솔 평문/ECS JSON 전환 + 파일 롤링) + 메트릭 |

## 규약

- **기본값은 `EnvironmentPostProcessor` + `addLast()`로 주입한다.** 최저 우선순위라 앱의 yml·환경변수가 항상 이긴다. 빈 재정의(`@ConditionalOnMissingBean`)보다 이쪽을 먼저 검토한다.
- **등록 위치가 두 곳이다.** 자동구성은 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, EnvironmentPostProcessor는 `META-INF/spring.factories`(Boot 4 신 키 `org.springframework.boot.EnvironmentPostProcessor`).
- **웹 스택을 전파하지 않는다.** `spring-boot-starter-webmvc`는 `compileOnly`로 두고 소비 앱이 직접 선언한다 — `api`로 두면 비웹 앱(gateway·realtime)까지 오염된다. 반대로 스타터의 존재 이유인 의존(`oauth2-resource-server`, `starter-kafka`)은 `api`다.
- `Swtp*StarterMarker` 클래스를 두고, 테스트는 그 빈의 존재로 자동구성 적용 여부를 판정한다.
- 테스트는 `ApplicationContextRunner`로 쓴다 — 컨텍스트 전체를 띄우지 않는다.
- 서비스 간 계약은 **JSON 구조로만** 유지한다. Kafka 컨슈머는 문자열로 받아 `EventEnvelopeParser`로 명시 파싱한다 — `JacksonJsonDeserializer`의 `__TypeId__` 방식은 소비측을 발행측 클래스명에 결합시킨다.

**기본값을 바꾸면 소비 앱 전체에 즉시 퍼진다.** 한 앱에만 필요한 값은 그 앱의 yml에 둔다.
