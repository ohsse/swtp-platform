# 스마트정수장 플랫폼 스캐폴딩 계획

> 기준 문서: [docs/architecture/스마트정수장_리빌드_아키텍처.md](../architecture/스마트정수장_리빌드_아키텍처.md)
> 진행 방식: Phase 단위로 순서대로 진행하며, 각 Phase 완료 시 `steps/step-XX-*.md`에 실행 내용과 결과를 기록한다.
> 모든 Phase는 종료 시점에 **`gradlew build` 성공 + 실행 가능 상태**를 유지한다.

> **후속 변경 (2026-08-21)** — Phase 4·5·9에서 결정한 Grafana 관측 스택은 철거되고 **ELK로 교체됐다.**
> 고객사 SFR-016이 ELK를 강제한다. 아래 Phase 기술은 결정 이력으로 남긴다.
> → 철거: [steps/step-16-observability-stack-teardown.md](steps/step-16-observability-stack-teardown.md)
> → 도입: [steps/step-17-elk-observability.md](steps/step-17-elk-observability.md)

## 확정 사항

- **전략**: 골격 먼저(전체 모듈 트리 + 인프라 compose) → 이후 수직 슬라이스 단위 구현
- **버전**: Java 21 + Spring Boot 4.1.0 + Spring Cloud 2025.1.2
- **Gradle**: 9.7.0 wrapper, Groovy DSL + build-logic Convention Plugin
- **DB 마이그레이션**: Flyway (스키마별 소유권 원칙과 정합)
- **Frontend**: 이번 범위 제외 (추후 결정)

## 검증된 버전 매트릭스 (2026-08 웹 검증)

| 구성요소 | 버전 | 비고 |
|---|---|---|
| Java | 21 (LTS) | toolchain으로 고정 |
| Gradle | 9.7.0 (wrapper) | |
| Spring Boot | 4.1.0 (2026-06 GA) | Framework 7 / Security 7 / **Jackson 3(tools.jackson)** 기반 |
| Spring Cloud | 2025.1.2 (Oakwood) | Boot 4.1 호환 공식 선언. Config/Eureka/Gateway 5.x 세대 |
| spring-kafka / Batch / Quartz / Flyway | Boot 4.1 BOM 관리 | Flyway 10+는 `flyway-database-postgresql` 별도 의존 필수 |
| Kafka Broker | 공식 `apache/kafka:4.x` (KRaft) | Bitnami 이미지 사용 금지(정책 변경) |
| PostgreSQL+TimescaleDB | `timescale/timescaledb-ha:pg17` | compose와 통합 테스트가 **같은 이미지**를 쓴다(리스크 5). `-ha` 태그에는 Timescale 패치 버전이 없으므로, 버전 고정이 필요해지면 다이제스트로 핀한다 |
| Observability | ~~Prometheus 3.x / Grafana 12.x / Loki 3.x / Alloy 1.x~~ → **Elastic Stack 9.4.5** | 철거(step-16) 후 ELK 도입(step-17). 4종(ES/Kibana/Filebeat/Metricbeat) 버전을 함께 올린다 |

**Spring Boot 4 주의**: 모듈화된 스타터 체계 — `spring-boot-starter-webmvc`(구 `-web`), `spring-boot-starter-flyway`, `spring-boot-starter-restclient` 등 신규 이름을 처음부터 사용한다. Gateway 아티팩트는 `spring-cloud-starter-gateway-server-webflux`.

## 아키텍처 검토 반영 사항

1. 앱 모듈 간 Gradle 의존 금지, 단일 DB + 스키마별 데이터 소유권, 자동/수동 Job 동일 로직 → build-logic과 Flyway 배치 구조로 강제
2. ~~**Eureka**: 단일 서버 compose에서는 서비스명 DNS로 충분 → Gateway는 정적 URI 라우팅으로 시작, 다중 서버 확장 시 활성화~~
   → **철회.** discovery-server를 Core로 올리고 Eureka를 실제 활성화했다. 전 앱이 `eureka-client`로
   자기를 등록하고 Gateway 라우트는 `lb://{서비스명}`이 된다. 결정적인 이유는 확장성이 아니라
   **설정 중복 제거**였다 — 정적 URI 방식은 같은 라우트 목록을 `config-repo/gateway-docker.yml`에
   복제해 인덱스 단위로 덮어써야 했고, 항목 하나만 빠져도 "컨테이너에서만 라우팅이 사라지는"
   실패가 성립했다. `lb://`는 호스트·포트를 적지 않으므로 그 복제본 자체가 없어진다. 아키텍처 3.1.2 참조
3. **Config Server**: native(파일시스템) 백엔드 + `config-repo/` 디렉토리로 시작. 레지스트리에는
   등록하지 않는다 — 설정을 받으려고 먼저 레지스트리를 조회해야 하는 순환을 피한다
4. **문서 보완 필요**: Data Collector 위치(Job Service 내부 여부) → 해당 슬라이스 시점에 결정 후 아키텍처 문서에 반영
   - ~~Realtime WebSocket 인증 방식~~ → Phase 10에서 **단기 티켓(`typ=ws-ticket`, 30초) 쿼리 파라미터** 방식으로 결정, 아키텍처 4.1에 반영 완료
5. **정수장별 인증 체계 차이**: `swtp.auth.mode` = `internal`(검증함) / `none`(검증 안 함) 2값으로 흡수한다.
   Gateway와 각 서비스가 독립 검증하는 다중 방어 구조라 스위치는 `config-repo` 한 곳에만 두고,
   `SWTP_AUTH_MODE`/`SWTP_AUTH_JWKS_URI` 환경변수가 `mode`와 `jwks-uri`를 한 쌍으로 결정한다. 아키텍처 4.1 참조
   (구 `external` 값은 제거 — `typ` 커스텀 클레임 때문에 표준 IdP 토큰으로는 통하지 않았다)
6. **EMS Core 승격**: 19장 예시 A·B 모두에 EMS가 포함되어 전 정수장 공통으로 확정 → compose profile 제거

## Phase 구성

### Phase 0 — 리포지토리 부트스트랩
- git init, .gitignore/.editorconfig/README, Gradle 9.7.0 wrapper, settings/build.gradle, gradle.properties, `../../gradle/libs.versions.toml`(버전 단일 통제점), scaffold 문서
- ✅ 판정: `gradlew --version`(Java 21), `gradlew projects` 성공, 첫 커밋

### Phase 1 — build-logic Convention Plugin (Groovy precompiled script plugin)
| Plugin | 대상 | 내용 |
|---|---|---|
| `swtp.java-common` | 전 모듈 | java, toolchain 21, UTF-8, JUnit Platform |
| `swtp.spring-library` | libs, starters | java-library + Boot BOM `platform()` + configuration-processor |
| `swtp.spring-boot-app` | 전체 apps | Boot plugin + BOM + actuator 기본 의존 + bootJar |
| `swtp.spring-cloud-app` | cloud 필요 앱 | 위 + Spring Cloud BOM `platform()` |
- `io.spring.dependency-management` 대신 Gradle 네이티브 `platform()` 사용
- build-logic의 settings.gradle에서 루트 버전 카탈로그 공유
- ✅ 판정: `gradlew build` 그린

### Phase 2 — libs + starters 골격 (앱보다 먼저 → 의존 방향 강제)
- `libs/swtp-common`: placeholder 클래스 + 단위 테스트 1개
- `starters/swtp-{web,kafka,observability,security}-starter`: `@AutoConfiguration` 골격 + `AutoConfiguration.imports`
- ✅ 판정: 빌드 그린 + ApplicationContextRunner 테스트로 자동구성 로드 확인

### Phase 3 — apps 11개 모듈 골격
- 포트 고정표: gateway 8080, config 8888, eureka 8761, master 8081, telemetry 8082, realtime 8083, job 8084, auth 8085, autonomous 8086, ems 8087, pms 8088
- config-server native(`config-repo/`), eureka server, gateway 정적 URI 라우팅
- `ai/ai-service`: FastAPI `/health` + requirements.txt + Dockerfile (Gradle 모듈 아님)
- 앱↔앱 project dependency 검출 시 빌드 실패시키는 검증 태스크
- ✅ 판정: 빌드 그린, config/gateway/master `bootRun` → `/actuator/health` 200

### Phase 4 — 인프라 docker-compose (미들웨어 단독 기동)
1. PostgreSQL+TimescaleDB: init SQL로 스키마 10개(master, telemetry, operation, ems, pms, auth, ai, job, batch, quartz) + extension. **테이블 DDL은 각 서비스 Flyway 소유**
2. Kafka KRaft single-node: auto-create 끄고 init 컨테이너로 토픽 8종 생성
3. Observability: Prometheus(정적 scrape) / Loki / Alloy(Docker logs discovery) / Grafana(프로비저닝)
- ✅ 판정: `docker compose up -d` → 스키마 10개, 토픽 8개, Grafana datasource 정상

### Phase 5 — 앱 컨테이너화 + 전체 스택 통합
- 공통 Dockerfile(bootJar layertools, temurin 21 JRE), Optional 서비스는 **compose profiles** (문서 19장 "정수장별 선택 배포" 구현)
- healthcheck=actuator + `depends_on: service_healthy` 체인
- ✅ 판정: 전체 기동 → gateway 경유 health 200, Grafana JVM 메트릭, Loki 로그

### Phase 6 — 수직 슬라이스 1: Master ("동기 API + 이벤트 발행 표준형")
- master 스키마 Flyway V1 + CRUD API, web-starter/kafka-starter Producer 실구현 → `master.changed` 발행
- ✅ 판정: Testcontainers(PG+Kafka) 통합 테스트, gateway 경유 CRUD, `master.changed` 컨슘 확인

### Phase 7 — 수직 슬라이스 2: Telemetry ("시계열 파이프라인")
- hypertable(chunk 7일)을 Flyway SQL로 관리, Consumer 적재, 조회 API, realtime SSE 최소 브로드캐스트
- ✅ 판정: kafka CLI 발행 → DB 적재 → API 조회 일치, `curl -N` SSE 수신

### Phase 8 — 수직 슬라이스 3: Job ("데이터가 스스로 흐르는 상태")
- job/batch/quartz 스키마(공식 DDL을 Flyway로, auto-init off), JobExecutionService + REST(202+executionId, Trigger Type), Quartz 1분 cron → Collector → `telemetry.raw`, Batch 집계(분→10분), `job.event`
- ✅ 판정: E2E — 수동 실행 API → Kafka → Telemetry 적재 → 조회/SSE, SCHEDULED/MANUAL 이력 구분

### Phase 9 — 관측·영속성 기반 정비 (로깅 정책 + 영속성 스타터)
- **선행**: 기동 불가 결함 제거 — compose 볼륨 미선언, init SQL 미마운트, DB 자격증명 SSOT(`swtp_dba`), `master-service` 포트, `.gitignore`/`.gitattributes` 신설
- `starters/swtp-persistence-starter` 신설: JDBC/Flyway/p6spy/`BaseEntity`·JPA Auditing을 이관. **JPA는 강제 전파하지 않는다**(master는 data-jpa, telemetry/job은 JdbcClient)
- 로깅 정책: **stdout(ECS JSON) 주 + 파일 롤링 보조**. Loki 경로는 stdout 단독(이중 적재 없음), 파일은 감사·장애분석 백업(일반/에러 분리, 일자+100MB 롤링, 30일)
- 게이트웨이 글로벌필터 1차: `X-Request-Id` 발급·전파 + 접근 로그. 서비스 측은 web-starter의 MDC 필터가 이어받는다
- 볼륨 정책: **데이터는 named volume, 로그는 bind mount**(용도별 분리). Loki compactor로 retention 실동작, Alloy는 `swtp-` 필터 + ECS JSON 파싱(level/service_name 라벨)
- ✅ 판정: `gradlew build` 그린, `docker compose config` 통과, stdout ECS JSON, `logs/<service>/` 일반·에러 파일, Loki에서 level/service_name 라벨 조회

### Phase 10 — 인증/인가 슬라이스 (+ 감사 컬럼 규약 재정의)
- **감사 컬럼 규약 2단화**: `BaseCreatedEntity`(등록 전용 — `rgstr_dttm`/`rgstr_id`) / `BaseEntity`(등록+수정 — `mdf_dttm`/`mdf_id`). *(Step 14에서 정정: 수정 컬럼은 nullable이 아니라 **NOT NULL**이며 등록 시점에 함께 채워진다 — "미수정"은 `mdf_dttm = rgstr_dttm`으로 판정)*
- **`AuditorProvider` SPI**(`swtp-common`): `AuditorAware`는 영속성 스타터가 독점하고(Spring Data가 `AUTOWIRE_BY_TYPE`으로 배선 — 빈이 둘이면 기동 실패) "현재 사용자"만 보안 스타터에 런타임 위임
- `swtp-security-starter` 실구현(서블릿): oauth2-resource-server 기반 **검증 전담** + `SecurityFilterChain` 기본값 + `SwtpPrincipal`/`@CurrentUser` + 통합 테스트용 `testFixtures`
- `auth-service`: **발급 전담** — RS256 + JWKS 공개, login/refresh(회전+재사용 탐지)/logout/ws-ticket, 저장소는 PostgreSQL `auth` 스키마(원칙 15·16: Redis 미사용). 서명키도 DB 보관(부분 유니크 인덱스로 활성 키 1개 보장)
- 게이트웨이: JWT 검증 글로벌필터(사용자 헤더 주입 + 클라이언트 위조 헤더 제거 + 토큰 용도 `typ` 검사) + CORS 중앙화. **Spring Security를 얹지 않고** `spring-security-oauth2-jose`의 디코더만 사용 — 인증 판단 지점을 하나로 유지
- **발급처 무관 검증**: 아키텍처 4.1이 auth-service를 Optional로 규정 → 검증 측은 서명키를 모르고 `swtp.auth.jwks-uri` 하나만 안다
- 미결 해소: Realtime WebSocket/SSE는 **30초 단기 티켓**(`typ=ws-ticket`) 쿼리 파라미터 방식으로 결정 → 아키텍처 문서 반영
- ✅ 판정: 토큰 없이 401, 유효 토큰 200 + `X-User-Id` 전달, 위조 헤더 덮어씀, 게이트웨이 우회 직접 호출도 401, 감사 컬럼에 토큰 `sub` 기록

### Phase 11 — API 문서 통합
- **문서 경로 자동 정렬**: `SwtpOpenApiEnvironmentPostProcessor`(web-starter)가 `spring.application.name`에서 `-service`를 떼어 `springdoc.api-docs.path=/api/{prefix}/v3/api-docs`를 주입. 게이트웨이 라우트에 StripPrefix가 없으므로 서비스가 같은 prefix로 노출해야 도달 가능하다. `addLast()`라 앱 명시값이 우선하고, 규칙 예외는 `swtp.openapi.route-prefix`로 지정
- **공통 명세 규약**: Bearer `SecurityScheme` + 전역 security 요구사항(집계 UI의 Authorize 근거) + **상대 서버 URL `/`** — springdoc 기본값(요청 Host로 계산)은 프록시 뒤에서 컨테이너 내부 주소를 스펙에 박는다
- 게이트웨이에 `springdoc-openapi-starter-webflux-ui`(webmvc 아님) + `swagger-ui.urls`로 8종 집계. url이 상대 경로라 `gateway-docker.yml` 복제가 불필요 — 라우트 리스트 치환 함정을 설정 단일화로 회피
- `ApiDocsRouteConsistencyTest`: 문서 URL ↔ 라우트 ↔ docker 프로파일 3자 정합성을 빌드 시점에 검증
- 잠복 결함 수정: `GlobalExceptionHandler`의 catch-all이 `NoResourceFoundException`을 삼켜 **미매핑 경로가 500 + ERROR 로그**였다 → COMMON-404로 격하
- ✅ 판정: swagger-config에 8종, 기동 서비스 5종 스펙 200, `servers:"/"`·`bearerAuth` 확인, 토큰 없이 401 → Authorize 후 200, 구 경로 404(ERROR 로그 0건)

## 핵심 파일 (정합성 유지 대상)
- `../../settings.gradle` — 모듈 트리의 단일 진실
- `../../gradle/libs.versions.toml` — 버전 통제점
- `../../build-logic/src/main/groovy/swtp.spring-boot-app.gradle` — 앱 규약 중심
- `infrastructure/docker/compose.yaml` — 전체 스택 + optional profile

## 리스크 및 대응
1. Cloud 2025.1.x ↔ Boot 4.1 성숙도 → Phase 3에서 config/discovery/gateway 최우선 기동 검증, 문제 시 Boot 4.0.x 하향(카탈로그 한 줄)
2. Jackson 3 전환 → kafka/web-starter에서 ObjectMapper 중앙 관리
3. Batch 6/Quartz 스키마 → 실제 의존 버전의 DDL 사용(`gradlew dependencies`로 확정)
4. Flyway 소유권 → 서비스별 `spring.flyway.schemas=<자기 스키마>` + history 테이블 자기 스키마 내부
5. TimescaleDB 테스트 → H2 대체 불가, Testcontainers 표준화
6. Windows 환경 → Docker Desktop + Testcontainers 확인, configuration cache 이슈 시 개별 비활성화
