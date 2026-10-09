# build-logic — Gradle Convention Plugin

Groovy precompiled script plugin. 루트 버전 카탈로그(`gradle/libs.versions.toml`)를 공유한다.

| 플러그인 | 대상 | 핵심 |
|---|---|---|
| `swtp.java-common` | 전 모듈 | toolchain 21, UTF-8, JUnit Platform, Lombok |
| `swtp.spring-library` | libs, starters | java-library + Boot BOM + configuration-processor |
| `swtp.spring-boot-app` | 전체 apps | Boot 플러그인, actuator, bootJar 단독, **앱간 의존 금지 검증**, **통합 테스트 기준형 검증** |
| `swtp.spring-cloud-app` | 전체 apps | 위 + Cloud BOM, **설정 전파(bus-kafka)**, 테스트에서 `eureka.client.enabled`·`spring.cloud.bus.enabled=false` |

## 규칙

- **`io.spring.dependency-management`를 쓰지 않는다.** Gradle 네이티브 `platform()`으로 통일한다.
- **전 모듈이 지켜야 하는 규칙은 여기에 못박는다.** 앱마다 yml이나 build.gradle에 적으면 새 모듈이 생길 때 빠진다 — 테스트의 Eureka 비활성화가 규약으로 올라온 사례다.
- **`swtp.spring-cloud-app`은 현재 apps 전체에 적용된다.** Cloud BOM만 얹던 규약이 아니다 — 설정 전파(`spring-cloud-starter-bus-kafka`)가 여기 있다. Bus는 대칭 의존이라(발행자·수신자 양쪽에 있어야 성립) "일부 앱"이라는 배치가 성립하지 않는다. 앱별 선언 시절 11개 중 2개에만 들어가 있었던 것이 규약으로 올린 이유다 — `docs/scaffold/steps/step-15-config-bus.md`.
- `jar` 태스크는 꺼져 있다. 켜면 `-plain.jar`가 함께 생겨 Dockerfile의 COPY 와일드카드가 두 개를 잡는다.
- Gradle 9는 JUnit Platform launcher를 자동 주입하지 않아 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`를 명시한다.
- `testFixtures` 소스셋은 별도 configuration이라 BOM이 상속되지 않는다 — `swtp.spring-library`가 `java-test-fixtures` 적용 시 한 번 더 정렬한다.
- **검증 두 개의 세기가 다르다.** 앱간 의존 금지는 configuration 시점에 세운다(있어서는 안 될 상태다). 통합 테스트 기준형은 `check`에서만 막는다(`verifyIntegrationTestBaseline`) — 스타터를 먼저 붙이고 테스트를 나중에 쓰는 **중간 상태에서도 `compileJava`·`bootRun`은 돌아야** 하기 때문이다. 규약 검증을 추가할 때 "어겨서는 안 되는 상태"인지 "아직 안 끝난 상태"인지로 붙일 지점을 고른다.
