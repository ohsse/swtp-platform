# Step 15 — 설정 전파(Config Bus)를 앱별 선언에서 규약으로

- 일자: 2026-08-20
- 상태: ✅ 완료 (빌드 통과, 컨테이너 E2E는 아래 "검증" 참조)
- 관련: [step-03-apps.md](step-03-apps.md), [step-13-log-format-switch.md](step-13-log-format-switch.md)

## 발단

`spring-cloud-starter-bus-kafka`가 도입돼 있었으나 의존이 **11개 앱 중 2개**(config-server,
master-service)에만 선언돼 있었다. 그런데 `config-repo/application.yml`은 **전 앱에**
`management.endpoints.web.exposure.include: busrefresh`를 내려주고 있었다 — 노출 목록과
실제 능력이 어긋난 상태였다.

원인은 배치 위치다. bus 의존이 각 앱의 `build.gradle`에 개별 선언돼 있어, 앱이 늘어날 때
빠뜨리는 것을 막을 장치가 없었다. `build-logic/CLAUDE.md`가 이미 경고한 실패 모드다 —
"앱마다 적으면 새 모듈이 생길 때 빠진다".

## 결정 (2026-08-20)

### 1. bus 의존은 `swtp.spring-cloud-app` 규약이 소유한다

**Spring Cloud Bus는 대칭 의존이다.** `/actuator/busrefresh`를 치면 그 앱이
`RefreshRemoteApplicationEvent`를 브로커에 **발행**하고, 같은 토픽을 **구독 중인 앱만**
자기 컨텍스트를 리프레시한다. 발행자에게만 의존을 넣는 것은 확성기만 사고 스피커를 안 단 것이다.

따라서 **"일부 앱"이라는 배치가 성립하지 않는다** — 전 앱이거나, 안 쓰거나 둘 중 하나다.
`swtp.spring-cloud-app`은 apps 11종 전부에 적용돼 있어 필요한 커버리지와 정확히 일치한다.
테스트의 Eureka 비활성화가 같은 논리로 먼저 규약에 올라온 선례다.

스타터(`starters/`)는 후보에서 뺐다. 스타터의 책임은 "우리 자동구성 + `EnvironmentPostProcessor`
기본값 주입"인데(`starters/CLAUDE.md`), bus는 서드파티 의존을 전파하기만 하면 된다 —
자동구성 클래스가 0개인 빈 스타터가 생긴다. `swtp-kafka-starter`에 얹는 것도 버렸다:
관심사가 다르고(도메인 이벤트 발행 vs 설정 전파), 그 스타터를 쓰는 앱이 4개뿐이라 7개가 또 빠진다.

### 2. 단일 서버인데도 bus를 쓴다

이 플랫폼은 정수장별 온프레미스 단일 서버 · 서비스당 인스턴스 1개라, Bus의 원래 이익인
"인스턴스 N개 fan-out"은 처음부터 없다. 그럼에도 채택한 근거는 둘이다.

1. **Kafka가 이미 필수 인프라다**(telemetry 파이프라인). 추가 미들웨어 비용이 0이라,
   Redis 금지(루트 불변식 4) 같은 "새 인프라 도입" 결정이 아니다.
2. **인스턴스는 1개씩이어도 서비스가 11개다.** `/actuator/refresh` 순회 스크립트는 앱이 늘 때마다
   갱신해야 하고, 그 갱신이 빠지는 실수는 방금 고친 "2/11"과 정확히 같은 종류의 실수다.

### 3. `spring.cloud.bus.destination`을 지정하지 않는다

config-server는 자기가 서빙하는 `config-repo`를 자기는 읽지 않는다(step-13 함정 2).
따라서 커스텀 토픽명을 쓰면 **`config-repo/application.yml`과 config-server 자기 yml 두 곳에
중복 선언이 불가피**하고, 두 값이 갈라지면 발행 토픽과 수신 토픽이 달라져 **에러 없이
아무 일도 일어나지 않는다.** 진단이 매우 어려운 실패 유형이다.

선언을 0개로 만들면 갈라질 여지 자체가 사라진다. Spring 기본 토픽명 `springCloudBus`를 쓰고,
`create-topics.sh`에 명시 생성한다(브로커가 `auto.create.topics.enable=false`이므로 필수다).

### 4. binder health를 서비스 health에서 분리한다

**이 결정이 이번 변경에서 가장 중요하다.** spring-cloud-stream은 `binders` health indicator를
`/actuator/health` 집계에 자동으로 넣는다. bus를 전 앱 규약으로 올리는 순간 **Kafka 장애가
11개 앱 전부를 DOWN으로 만들고**, compose의 `depends_on: service_healthy` 체인이 끊겨
스택 전체가 기동하지 못한다. config-server가 그 체인의 뿌리라 파급이 특히 크다.

`management.health.binders.enabled: false`로 분리한다. **bus는 optional 성격이어야 한다** —
설정을 갱신하지 못할 뿐, 서비스는 계속 동작해야 한다. config/eureka 클라이언트에 대해
`apps/CLAUDE.md`가 이미 세워둔 원칙("서버가 없어도 재시도만 할 뿐 기동을 막지 않는다")과 같다.

### 5. 토픽 생성 완료를 기다리는 범위를 넓힌다 — config-server만 예외

`x-depends-kafka`(kafka healthy + kafka-init 완료)가 걸린 앱은 도메인 이벤트를 쓰는 4개뿐이었다.
이제 11개 앱 전부가 `springCloudBus`를 구독하므로, 나머지는 토픽이 생기기 전에 뜰 수 있다.
깨지지는 않는다 — binder가 AdminClient로 토픽을 직접 만든다(브로커의 `auto.create.topics.enable=false`는
*클라이언트 요청에 의한 암묵적* 생성만 막고 명시적 `CreateTopics` API는 막지 않는다).
그러나 그러면 **토픽 생성 주체가 스크립트가 아니라 binder가 되는 경우가 생겨** 인프라 원칙이 조용히 우회된다.

**config-server만 뺀다.** 기동 체인의 뿌리라 여기에 Kafka를 걸면 스택 전체 기동이 Kafka 준비만큼
늦어지고, 설정 평면이 이벤트 평면에 종속된다. config-server는 발행자이므로 늦게 붙어도 무해하다 —
기동 직후에 busrefresh를 칠 일이 없다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `build-logic/.../swtp.spring-cloud-app.gradle` | bus-kafka 의존 + 테스트에서 `spring.cloud.bus.enabled=false` |
| `apps/config-server/build.gradle` | bus-kafka 개별 선언 **제거** (규약이 소유) |
| `apps/master-service/build.gradle` | 동일 |
| `apps/config-server/.../application.yml` | `bus.destination` 제거, kafka 주소를 환경변수화(`:localhost:9092`), binder health 분리 |
| `infrastructure/docker/compose.yaml` (kafka) | **브로커 포트 9092 통일** — 광고 주소 `HOST://localhost:9092`, 매핑 `9092:9094` |
| `apps/{job,realtime,telemetry}-service/.../application.yml` | 부트스트랩 주소 9094 → 9092 |
| `config-repo/application.yml` | `bus.destination` 제거, binder health 분리, `big.company.*` 더미 키 제거 |
| `config-repo/application-dev.yml` | kafka 주소 재정의 **제거** (함정 1) |
| `infrastructure/kafka/create-topics.sh` | `springCloudBus` 토픽 명시 생성 (파티션 1) |
| `infrastructure/docker/compose.yaml` | config-server에 `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092` |
| `infrastructure/docker/compose.yaml` (depends_on) | `*depends-kafka`를 **config-server 제외 전 앱**으로 확대 (결정 5) |
| 문서 | `build-logic`·`config-repo` 두 CLAUDE.md |

## 함정 기록

1. **Kafka 부트스트랩 주소가 네 갈래로 갈려 있어 bus가 실제로는 동작하지 않았다.**
   호스트 리스너는 9094인데(`HOST://localhost:9094`) config-server 자기 yml과
   `application-dev.yml`이 둘 다 `localhost:9092`였다. master-service는 `profiles.active: dev`
   고정이라 그 9092를 받는다.
   **배치를 고쳐도 이걸 안 고치면 발행자와 수신자가 같은 브로커를 보지 못한다.**

   해법으로 **브로커 포트를 전 경로 9092로 통일**했다 — 앱 설정에서 달라지는 것이
   호스트명뿐이면(`localhost:9092` / `kafka:9092`) 애초에 갈릴 자리가 없다.
   컨테이너 안에서는 한 포트에 두 리스너를 바인딩할 수 없으므로 HOST 리스너는 9094에 두고
   `ports: "9092:9094"`로 감췄다.

   **`KAFKA_ADVERTISED_LISTENERS`를 함께 고쳐야 한다** — 클라이언트는 부트스트랩 응답으로
   받은 광고 주소로 **재접속**하므로, 포트 매핑만 바꾸고 광고를 `localhost:9094`로 두면
   호스트의 클라이언트가 다시 9094를 찾아가 조용히 실패한다.
   컨테이너 안 healthcheck의 `localhost:9092`는 INTERNAL 리스너라 그대로 유효하다.

2. **config-server만 환경변수가 필요하다.** 자기가 서빙하는 config-repo를 읽지 못하므로
   자기 yml 값이 최종값인데, 포트는 같아도 호스트명이 다르다(`localhost` / `kafka`).
   step-13이 `SWTP_LOG_FORMAT`에 쓴 방법 그대로 compose 블록에서 직접 전달한다 —
   config-server는 `environment: *app-env` 앵커를 쓰지 않는 유일한 앱이다.

3. **테스트에서 bus를 끄지 않으면 Kafka 없는 앱의 테스트가 느려진다.**
   규약이 전 앱에 붙으므로 Kafka Testcontainer가 없는 6개 앱
   (gateway·auth·ems·pms·autonomous·discovery-server)이 브로커 연결을 재시도한다.
   더 나쁜 경우, 개발 PC에 브로커가 떠 있으면 테스트 컨텍스트가 운영 토픽의 리프레시 이벤트를 받는다.
   Eureka와 같은 자리(`tasks.withType(Test)`)에 못박았다.

4. **gateway(WebFlux)에 서블릿 스택이 딸려오지 않는다.** 규약이 전 앱에 붙으므로 확인이 필요했다.
   `:apps:gateway:dependencies` 실측 결과 `spring-cloud-stream-binder-kafka` → `spring-kafka`까지만
   들어오고 `tomcat-embed-core`/`spring-webmvc`/`jakarta.servlet`은 없다.
   (`tomcat-embed-el`은 들어오지만 EL 표현식 라이브러리라 서블릿 컨테이너가 아니다.)

5. **`big.company.*`는 읽는 코드가 없었다.** bus 동작 확인용 더미로 들어왔던 것으로 보이나,
   소비자가 없어 리프레시 성공 여부를 확인할 수 없었다. 제거하고, 검증은 실제 소비되는
   `@ConfigurationProperties` 키로 한다(아래).

## 알려진 한계

1. **`@RefreshScope`가 저장소에 0건이다.** 리프레시 대상은 `@ConfigurationProperties` 빈들뿐이며,
   이들은 `ConfigurationPropertiesRebinder`가 `@RefreshScope` 없이도 재바인딩한다 —
   `SwtpAuthProperties`·`SwtpSecurityProperties`(security-starter), `GatewayAuthProperties`·
   `GatewaySecurityProperties`(gateway), `AuthProperties`(auth-service).
   일반 빈에 주입된 설정을 갱신하려면 그때 `@RefreshScope`를 명시해야 한다.

2. **`@Value` 주입 지점은 리프레시되지 않는다.** `SampleQuartzConfig`,
   `SwtpOpenApiAutoConfiguration`, `SwtpJpaAuditingAutoConfiguration` 3곳이 해당한다.

3. **`logging.config`는 리프레시 대상이 아니다.** 로그 포맷은 기동 시점에 굳는다
   (step-13 알려진 한계 3). 전환에는 재기동이 필요하다.

4. **`swtp.auth.mode`는 리프레시로 바꾸지 않는다.** 환경변수(`SWTP_AUTH_MODE`)가 SSOT이고
   (`config-repo/CLAUDE.md`), config-server는 플레이스홀더를 해석하지 않고 원문 그대로 내려보낸다
   (step-13 함정 5). 즉 각 앱의 환경변수가 바뀌지 않는 한 리프레시해도 같은 값이다.

5. **Kafka가 죽으면 설정 전파가 멈춘다.** health에서는 분리했으므로 서비스는 계속 동작하지만,
   busrefresh는 조용히 실패한다. 단일 서버라 Kafka도 같은 서버에 있다는 점이 이 한계의 배경이다.

6. **`profiles.active: dev` 하드코딩은 이번 범위 밖이다.**
   `apps/master-service/.../application.yml`이 dev를 고정하고 있고, `application-dev.yml`의 추가로
   `config-repo/CLAUDE.md`의 "프로파일 축은 환경 축(docker) 하나다" 규칙이 이미 깨져 있다.
   컨테이너에서는 compose의 `SPRING_PROFILES_ACTIVE`가 이겨 동작에 문제는 없으나, 별도 건이다.

## 검증

```bash
./gradlew build                                                  # 전체 빌드 + 통합 테스트
./gradlew :apps:gateway:dependencies --configuration runtimeClasspath   # 함정 4
```

컨테이너 E2E:

```bash
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml up -d --build
docker exec swtp-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 --list | grep springCloudBus
```

1. 대상 값을 읽는다 — `curl localhost:8080/master-service/actuator/env/swtp.security.<키>`
2. `config-repo/application.yml`에서 그 키를 바꾼다.
   **`swtp.auth.mode`는 검증용으로 쓰지 않는다** — 환경변수가 SSOT라 파일을 바꿔도 값이 그대로다(한계 4).
3. `curl -XPOST localhost:8888/actuator/busrefresh`
4. **11개 앱 전부**의 로그에 `Received remote refresh request`가 떠야 한다 — 이번 변경의 핵심 성공 조건이다.
5. 1번을 다시 읽어 값이 바뀌었는지 확인한다.

회귀 확인:

```bash
docker compose -f infrastructure/docker/compose.yaml stop kafka
docker compose -f infrastructure/docker/compose.yaml restart config-server
curl -fsS localhost:8888/actuator/health     # UP이어야 한다 (결정 4)
```
