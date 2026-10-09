# Step 09 — 관측·영속성 기반 정비 (로깅 정책 + 영속성 스타터)

- 일자: 2026-08-14
- 상태: ✅ 완료 (2026-08-14 Phase 10 작업 중 보류 항목 전부 검증 — 아래 "후속 검증" 참조)
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 9

> **후속 변경 (2026-08-14)** — 콘솔 로그 포맷은 더 이상 ECS 고정이 아니다.
> `SWTP_LOG_FORMAT`(`plain` 기본 / `ecs`)이 정수장별로 고르며, `swtp-logback.xml`은
> `swtp-logback-{common,plain,ecs}.xml` 셋으로 분리됐다. 아래 "stdout(ECS JSON)이 주" 결정 중
> **파일을 Loki 수집원으로 삼지 않는다**는 부분은 그대로 유효하다. → [step-13](step-13-log-format-switch.md)

> **후속 변경 (2026-08-21)** — 여기 기록된 Grafana 관측 스택(Alloy/Loki/Prometheus/Grafana)은
> 철거됐다. 고객사 요구사항 SFR-016이 ELK를 강제한다. 앱 계측(actuator·ECS 로그·requestId MDC)은
> 그대로 유지되며, ELK가 붙을 접점은 별도 문서에 명세돼 있다.
> → [step-16](step-16-observability-stack-teardown.md)

## 목표

Phase 0~8로 골격과 수직 슬라이스 3종이 완성된 뒤 남은 다섯 주제 중 **로깅 정책·영속성 규약·볼륨 정책**을 정착시킨다.
p6spy를 어느 스타터에 둘지, 로그 파일을 어떻게 남기고 Loki와 어떻게 연동할지, 볼륨을 named로 갈지 bind로 갈지를 결정하고 구현한다.

> 결정 (2026-08-14):
> - **영속성은 별도 스타터로 분리** — p6spy가 `swtp-web-starter`에 들어가 있던 것은 관심사 오염이다.
>   DB를 쓰지 않는 앱(gateway/realtime)에 JDBC 로깅이 딸려가면 안 된다. JPA/JDBC/Flyway/DataSource가
>   앱마다 복붙되던 것도 함께 흡수한다.
> - **로그는 stdout(ECS JSON)이 주, 파일은 보조** — Loki로 가는 경로는 stdout 단독으로 유지한다
>   (step-04의 "앱은 stdout만, Alloy가 수집" 결정 승계). JSON 한 줄이라 Java 스택트레이스가
>   여러 엔트리로 쪼개지는 문제가 원천 소멸한다. 파일은 감사·장애분석 백업 전용이라 Alloy가 읽지 않는다 → **이중 적재 없음**.
>   대안 기각: 파일을 Loki 수집원으로 삼으면 Alloy에 멀티라인 병합 스테이지와 컨테이너별 볼륨 마운트 관리가 필요해진다.
> - **볼륨은 용도별 분리** — 데이터(DB/Loki/Grafana/Prometheus)는 named volume(Docker가 수명주기 관리),
>   로그는 bind mount(호스트에서 직접 열람·반출이 목적).
> - **JPA는 스타터가 강제 전파하지 않는다** — master는 data-jpa, telemetry/job은 JdbcClient라는 기존 결정
>   (Phase 7·8)을 스타터가 뒤집으면 안 된다. 공통 분모인 JDBC만 `api`로 올린다.

**영구 산출물**: persistence 스타터, 공통 로깅 설정(`swtp-logback.xml`), 요청 상관관계 ID 규약, Alloy/Loki 보정, 볼륨 정책, `.gitignore`/`.gitattributes`.

## 실행 내용

### 1. 선행 — 기동 불가 결함 제거

`docker compose config`가 **파싱 자체를 거부**하던 상태였다. 새 작업의 검증(컨테이너 기동)이 불가능하므로 먼저 걷어냈다.

| 결함 | 원인 | 조치 |
|---|---|---|
| `service "timescaledb" refers to undefined volume swtp_flatform_timescaleDB` | 오타 볼륨명이 top-level `volumes:`에 미선언 (선언된 `timescaledb-data`는 orphan) | `swtp-timescaledb-data`로 통일 |
| 스키마 10종 미생성 | `postgresql/init/`이 어디에도 마운트되지 않음 | `../postgresql/init:/docker-entrypoint-initdb.d:ro` 추가 |
| DB 자격증명 불일치 | 컨테이너 기본(postgres/postgres) vs 앱(swtp/swtp) | `00-bootstrap.sql`이 `swtp_dba`/`swtp`를 만드는 SSOT로 확정, healthcheck도 `swtp_dba` 기준 |
| healthcheck 조기 통과 | `pg_isready`는 init SQL 실행 중에도 소켓 응답만으로 통과 | **스키마 존재 여부**로 판정 (`information_schema.schemata`에 master) |
| `master-service` 랜덤 포트 | 직전 커밋에서 `8081` → `0` | `8081` 원복 (compose healthcheck·Prometheus 타깃·gateway `depends_on` 체인이 이 포트에 의존) |
| Flyway 미동작 | 직전 커밋에서 의존성 주석 처리 (`V1__sample_item.sql`은 존재) | persistence-starter가 흡수 |
| `.dockerignore`/`.gitattributes` 부재 | step-04·05 문서는 존재한다고 기록했으나 실물 없음 | `.gitignore`/`.gitattributes` 신설 (`*.sh`/`*.alloy`/`*.yaml` LF 고정 — CRLF 체크아웃 시 shebang 파손 방지) |

> 이 중 compose·init SQL·자격증명·포트 항목은 작업 중 **사용자가 직접 정리**했고, 본 단계는 그 위에 이어서 진행했다.

### 2. `starters/swtp-persistence-starter` 신설

```
swtp-persistence-starter/
├── SwtpPersistenceAutoConfiguration      # 마커
├── SwtpJpaAuditingAutoConfiguration      # JPA Auditing (조건부)
├── SwtpPersistenceEnvironmentPostProcessor
├── BaseEntity                            # createdAt/updatedAt @MappedSuperclass
└── resources/spy.properties
```

**의존성 설계 — JPA 강제 전파 금지**

```groovy
api 'org.springframework.boot:spring-boot-starter-jdbc'      // 공통 분모
api 'org.springframework.boot:spring-boot-starter-flyway'
api libs.p6spy                                                // 드라이버 런타임 로딩 필요
runtimeOnly 'org.flywaydb:flyway-database-postgresql'
runtimeOnly 'org.postgresql:postgresql'
compileOnly 'org.springframework.boot:spring-boot-starter-data-jpa'  // BaseEntity 컴파일용만
```

- `p6spy:p6spy:3.9.1`을 `libs.versions.toml`로 이동 (버전 단일 통제점 원칙 — web-starter에 하드코딩돼 있던 것)
- `swtp-web-starter`에서 p6spy 제거
- master/telemetry/job의 개별 jdbc/flyway/postgresql 선언을 스타터 의존 한 줄로 대체

**JPA Auditing 조건을 두 겹으로 건 이유** — 클래스패스 조건만으로는 기동이 깨진다:

```java
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
```

`@EnableJpaAuditing`이 등록하는 `jpaAuditingHandler`는 생성자에서 `jpaMappingContext`를 참조한다.
클래스패스 조건만 걸면 "JPA 클래스는 있으나 인프라는 아직 없는" 순간에 켜져 `BeanCreationException`이 난다(테스트로 재현 확인).
`@ConditionalOnBean`은 자동구성에서만 순서가 보장되므로 **별도 최상위 자동구성**으로 분리해 `imports`에 2줄로 등록했다.

> Boot 4 패키지 이동: JPA 자동구성이 `org.springframework.boot.autoconfigure.orm.jpa` → **`org.springframework.boot.hibernate.autoconfigure`**.
> jar 실물로 확인 — step-08의 Batch 6 패키지 이동과 같은 계열의 함정이다.

**`spy.properties`** — appender를 SLF4J로 고정하는 것이 핵심이다. 기본 `SystemOutLogger`는 로거를 우회한 raw stdout이라
레벨 제어가 불가능하고 구조화 로깅에도 실리지 않는다.

```properties
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(executionTime)ms | %(category) | %(sql)
excludecategories=info,debug,result,resultset,commit,rollback
outagedetection=true
outagedetectioninterval=2
```

로거명이 `p6spy`이므로 `logging.level.p6spy`로 온오프한다.

**EnvironmentPostProcessor가 주입하지 않는 것**: `flyway.schemas` / `hibernate.default_schema` / `hikari.schema`.
스키마는 서비스별 소유권 사항이라 공통 계층이 기본값을 주면 사고가 난다 — 실제로 직전에
`config-repo/application.yml`(전 서비스 공통)에 `hibernate.default_schema: master`가 들어가 전 서비스에 배포된 전례가 있다.

### 3. 구조화 로깅 + 파일 롤링 (`swtp-observability-starter`)

Boot 4 내장 구조화 로깅을 쓰므로 `logstash-logback-encoder` 같은 외부 의존성이 필요 없다.
다만 **일반/에러 파일 분리는 내장 프로퍼티로 표현 불가**하므로 `swtp-logback.xml`을 둔다.

- **CONSOLE**: Boot 제공 조각 `structured-console-appender.xml` 재사용 (ECS 포맷) → Alloy가 수집하는 유일한 경로
- **FILE**: `logs/<service>/<service>.log`, 평문(사람이 읽는 용도), 일자+100MB 롤링, 30일/3GB 상한
- **ERROR_FILE**: 동일 롤링 + `ThresholdFilter level=ERROR` → 장애 시 이 파일만 보면 된다

EPP 기본값 추가: `logging.config=classpath:swtp-logback.xml`, `logging.structured.format.console=ecs`, `swtp.logging.path=logs`.

`logging.config` 주입이 유효한 이유는 리스너 순서다 — `EnvironmentPostProcessorApplicationListener`(HIGHEST_PRECEDENCE+10)가
`LoggingApplicationListener`(+20)보다 먼저 실행되므로 로깅 초기화 시점에 값이 이미 환경에 있다.
(계획 단계에서 리스크로 잡았던 항목이며, 테스트로 해소했다)

### 4. 게이트웨이 글로벌필터 1차 — 요청 ID + 접근 로그

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| `SwtpHeaders` | `libs/swtp-common` | `X-Request-Id` / MDC 키 상수 — gateway(WebFlux)와 서비스(서블릿)가 공유해야 하므로 웹 스택 비의존 라이브러리에 둔다 |
| `RequestIdGlobalFilter` | gateway, `HIGHEST_PRECEDENCE` | 없으면 발급, 있으면 이어받음(호출측 추적 연결). 다운스트림 요청 + 응답 헤더 + exchange 속성에 세팅 |
| `AccessLogGlobalFilter` | gateway, `+10` | 메서드·경로·상태·라우트ID·타깃URI·소요시간을 SLF4J fluent key-value로 → ECS 개별 필드 |
| `RequestIdMdcFilter` | web-starter, `HIGHEST_PRECEDENCE` | 전파된 ID를 MDC에 적재. 게이트웨이 미경유 직접 호출도 자체 발급(ID 없는 로그 방지), `finally`로 반드시 제거(스레드 풀 누출 차단) |

접근 로그를 `doFinally`로 기록하는 이유: SSE/WebSocket 스트리밍 응답은 체인이 즉시 완료되지 않는다.
종료 시그널 시점에 기록해야 실제 소요시간과 종료 사유(정상/취소)가 남는다.

### 5. 볼륨 정책 + Alloy/Loki 보정

- **로그 bind mount**: `../../logs:/logs` + `SWTP_LOGGING_PATH=/logs`를 앱 공통 앵커에 배치.
  단 **compose YAML 앵커는 리스트를 병합하지 못하고 통째 치환**하므로, 이미 볼륨을 가진 config-server는 항목을 직접 나열했다
- **Loki compactor 추가** — `retention_period`는 선언일 뿐이고 실제 삭제 주체는 compactor다.
  없으면 `loki-data`가 무한 증가한다. Loki 3.x는 `delete_request_store`까지 요구하며, 빠뜨리면 기동 실패
- **Alloy**: `swtp-` 접두 필터(무관한 호스트 컨테이너 유입 차단), `loki.process`로 ECS JSON 파싱 →
  `level`/`service_name` 라벨 승격, `docker.sock`을 `:ro`로, positions 볼륨 추가
- **`container` 라벨 유지** — `jvm-overview.json`이 `{container="swtp-$application"}`에 하드코딩 의존한다
- **`request_id`는 라벨로 올리지 않는다** — 요청마다 값이 달라 Loki 스트림 카디널리티가 폭발한다. 로그 본문에만 남긴다

### 6. 통합 테스트 격리 — 테스트가 로컬 config-server를 타던 문제

세 서비스 통합 테스트가 전부 `schema "..." does not exist`로 실패했다. 원인은 **개발자 PC에 config-server가 떠 있던 것**이다.

`spring.config.import: "optional:configserver:..."`의 `optional:`은 "config-server가 없어도 기동된다"는 뜻이지
"테스트에서는 안 쓴다"가 아니다. config-server가 살아 있으면 통합 테스트가 조용히 원격 설정
(`spring.flyway.create-schemas: false`)을 끌어오고, Testcontainers에는 `01-schemas.sql`이 없으니 Flyway가 실패한다.
**테스트 결과가 로컬 환경 상태에 좌우되던** 셈이다.

`AbstractIntegrationTest` 3개(표준형)에 동일 조치:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "spring.config.import=",              // 원격 설정 차단 — 테스트는 자기 설정만으로 돈다
        "spring.flyway.create-schemas=true"   // 운영은 01-schemas.sql이 만들지만 Testcontainers엔 없다
})
```

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| `jpaAuditingHandler` 생성 실패 (`jpaMappingContext` 미해결) | `@ConditionalOnClass`만으로는 JPA 인프라 구성 전에 auditing이 켜진다 | 별도 자동구성으로 분리 + `after = HibernateJpaAutoConfiguration` + `@ConditionalOnBean` |
| logback 롤링 정책 기동 실패 (`maxFileSize property is mandatory`) | `springProperty`의 `defaultValue`가 적용되지 않아 `LOG_MAX_FILE_SIZE_IS_UNDEFINED`가 흘러듦 | 사용처에 logback `:-` fallback 병기 (Boot 제공 조각들과 동일 관례) |
| logback 테스트가 통과하는데 파일은 엉뚱한 경로에 생성 | `<springProperty>`는 Boot 전용 태그 — 일반 `JoranConfigurator`는 **오류 없이 조용히 무시**한다 | 실제 `SpringApplication`을 띄우는 테스트로 전환 (기동 경로 그대로 검증) |
| Windows에서 `@TempDir` 삭제 실패 | 컨텍스트를 닫아도 RollingFileAppender 파일 핸들이 남는다 | 테스트 로그를 `build/test-logs/`에 기록 (JUnit 삭제 대상에서 제외) |
| 통합 테스트 3종 전멸 (`schema does not exist`) | 로컬 config-server가 떠 있어 `optional:` import가 원격 설정을 끌어옴 | `spring.config.import=` 로 차단 + 테스트에서만 `create-schemas=true` |
| `JpaAuditingRegistrar` 타입 참조 불가 | package-private 클래스 | 빈 이름(`jpaAuditingHandler`)으로 단언 |

## 검증 결과

```text
> gradlew build                                          BUILD SUCCESSFUL ✅
  (persistence-starter 6 + observability-starter 8 + web-starter 7 + gateway 필터 4
   + 기존 통합 테스트 master 4 / telemetry 3 / job 5 — 전부 통과)

> docker compose -f infrastructure/docker/compose.yaml config --quiet
  통과 ✅  (직전까지 "refers to undefined volume ... invalid compose project"로 파싱 거부)

> docker run --rm grafana/alloy:v1.18.1 fmt /etc/alloy/config.alloy       ALLOY SYNTAX OK ✅
> docker run --rm grafana/loki:3.7.6 -verify-config                       "config is valid" ✅ (compactor 포함)

[관측 스택 + config-server 기동]
psql \dn → ext, master, operation, telemetry, ems, pms, auth, job, batch, quartz ✅
  (01-schemas.sql 마운트 증명 — 확장 격리 스키마 ext 포함)

docker logs swtp-config-server | head -1
{"@timestamp":"...","log":{"level":"INFO","logger":"...ConfigServerApplication"},
 "service":{"name":"config-server","version":"1.0.0"},"message":"Starting ...","ecs":{"version":"8.11"}}
  → stdout ECS JSON 1줄 ✅ (스택트레이스 분해 문제 원천 차단)

ls logs/config-server/
  config-server.log  config-server-error.log      → 일반/에러 분리 + bind mount ✅

Alloy 컴포넌트 5종 전부 health=healthy ✅
  (discovery.docker / discovery.relabel / loki.source.docker / loki.process / loki.write)

curl loki/api/v1/labels                → ["container","level","service_name"] ✅
curl .../label/level/values            → ["INFO"]                (ECS JSON 파싱 증명) ✅
curl .../label/container/values        → swtp-* 8종만            (접두 필터 증명) ✅
curl .../label/service_name/values     → config-server(앱) + swtp-*(미들웨어) ✅

[master-service 통합 테스트 로그에서 부수 확인]
HikariCP 풀 이름 "master-service-pool"  → EPP 기본값 적용 증명 ✅
```

## 후속 검증 (2026-08-14, Phase 10 작업 중)

포트 예약은 여전히 남아 있으나(호스트 환경 문제라 관리자 권한 없이는 해소 불가),
**호스트 포트 게시만 제거하는 override**(`infrastructure/docker/compose.no-publish.yaml`)로 우회해 검증을 마쳤다.
컨테이너 내부 포트와 컨테이너 간 통신은 Windows 포트 예약의 영향을 받지 않으므로,
docker 네트워크 안에서 curl 컨테이너로 호출하면 실제 동작을 그대로 확인할 수 있다.

```text
전 앱 컨테이너 healthy (gateway/master/telemetry/job/auth/realtime + config)  ✅
gateway 경유 X-Request-Id 왕복 — 응답 헤더 1개(중복 없음)                     ✅
게이트웨이 접근 로그 — requestId/http.*/gateway.route/duration.ms 구조화 필드  ✅
401로 끊긴 요청도 접근 로그에 기록 (필터 순서 증명)                            ✅
master-service 로그에 동일 requestId (MDC 상관관계, p6spy SQL 로그 포함)       ✅
호스트 logs/<service>/ 7개 디렉토리 + 일반/에러 파일 생성                      ✅
```

**이 과정에서 접근 로그 결함 2건을 발견해 고쳤다** — 아래 "문제와 해결(후속)" 참조.

## 문제와 해결 (후속)

| 문제 | 원인 | 해결 |
|---|---|---|
| 접근 로그가 한 줄도 남지 않음 (요청은 200) | `exchange.getAttribute()`의 반환 타입이 `<T> T`라 컴파일러가 `addKeyValue(String, Supplier)` 오버로드를 선택 → 매 요청 `ClassCastException`. `doFinally` 안이라 Reactor가 `onErrorDropped`로 삼켜 조용히 사라졌다 | 지역변수로 받아 `String`으로 타입 확정. **로그 출력 자체를 단언하는 테스트**(`AccessLogGlobalFilterTest`)를 추가 — 기존 테스트가 `getOrder()`만 봐서 놓친 결함이다 |
| 응답에 `X-Request-Id`가 2번 실림 | 게이트웨이가 체인 이전에 세팅하고, 다운스트림 서비스가 보낸 동명 헤더를 게이트웨이가 다시 복사 | `beforeCommit`에서 `set()`으로 한 값만 남긴다. 라우팅 전 응답(401 등)을 위해 사전 세팅은 유지 |

## 결정 사항 및 알려진 한계

- **개발 PC의 포트 예약은 미해소** — Windows가 TCP **7981–8380**을 동적 예약해 앱 포트(8080~8088)
  게시가 거부된다(`bind: An attempt was made to access a socket in a way forbidden...`).
  코드/설정 문제가 아니라 호스트 환경 문제다. 관리자 권한으로 `net stop winnat && net start winnat`
  또는 `netsh int ipv4 set dynamic tcp start=49152 num=16384` 후 재부팅으로 해소된다.
  해소 전까지는 위 override 파일로 기동하고 docker 네트워크 안에서 호출한다
  (호스트 브라우저에서 Grafana 3000·config-server 8888은 예약 범위 밖이라 정상 접근된다)
- **`service_name` 라벨이 혼재한다** — ECS JSON을 내는 앱은 애플리케이션명(`config-server`),
  평문을 내는 미들웨어는 컨테이너명(`swtp-kafka`, Loki 3.x 자동 부여). 의도된 동작이나 대시보드 작성 시 유의
- **p6spy는 URL 접두 방식(`jdbc:p6spy:`)을 유지한다** — 계획 단계에서 `P6DataSource` 래핑을 제안했으나,
  작업 중 config-repo가 `swtp.db.host/port` 파라미터화로 URL 이원화 문제를 이미 해소해 전환 실익이 사라졌다.
  온오프는 `logging.level.p6spy`로 한다
- **파일 appender는 동기** — `AsyncAppender` 미적용. bind mount 디스크가 느리면 요청 스레드가 블로킹될 수 있다.
  운영 부하 측정 후 재검토(비동기는 프로세스 급사 시 버퍼 유실이 대가)
- **요청 ID는 게이트웨이 경유 구간만 덮는다** — 아키텍처 3.1.3이 "서비스 간 내부 호출은 Gateway를 거치지 않는다"고
  규정하므로, 서비스 간 직접 호출 시 헤더 릴레이가 별도로 필요하다. 실제 서비스 간 호출이 생기는 시점의 과제
- **분산 트레이싱 없음** — micrometer-tracing/Tempo 미도입. 요청 ID로 로그 상관관계까지만 성립한다
- **`config-repo` 평문 비밀번호 잔존** — `swtp_local_dev`이 리터럴로 남아 있다. 운영 배포 시 secret 대체 필요
- **HikariCP 총량** — 앱 11개 × 기본 10 = 110 > PostgreSQL 기본 `max_connections` 100.
  `--profile full` 전체 기동 시나리오에서 재검토

## 다음 단계

Phase 10(인증/인가 슬라이스) — [step-10-auth-slice.md](step-10-auth-slice.md) 참조.
`swtp-security-starter` 검증 구현 + `auth-service` 발급(RS256/JWKS) + 게이트웨이 JWT 필터·CORS를 완료했고,
그 과정에서 이 Step의 보류 항목까지 함께 검증했다.
