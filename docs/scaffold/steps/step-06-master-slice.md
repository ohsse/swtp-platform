# Step 06 — 수직 슬라이스 1: Master (동기 API + 이벤트 발행 표준형)

- 일자: 2026-08-12
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 6

## 목표

첫 수직 슬라이스로 "동기 API + 이벤트 발행" 표준형을 확립한다.
**샘플 도메인은 일회용**이고, 영구 산출물은 재사용 패턴이다:
web-starter 실구현(ErrorCode/전역 예외/OpenAPI), kafka-starter Producer + 이벤트 봉투,
Flyway 배선 규약, Testcontainers 통합 테스트 표준형, compose/gateway 검증 루틴.

> 결정: 레거시 스키마는 사용하지 않는다. 실제 master DB 설계는 차후 진행하고,
> 이번에는 임의 샘플 테이블 1개(`sample_item`)로 파이프라인 전체를 관통 검증한다.

## 실행 내용

### 1. swtp-common — 에러 코드 계약 (`libs/swtp-common`)

- `ErrorCode` 인터페이스: `getCode()` / `getHttpStatus()`(**int** — 프레임워크 비의존 유지) **2요소**
  - 에러 메시지는 서버가 내려주지 않는다 — **프론트가 code 기반 i18n으로 표현 책임** (Phase 6 후속 결정)
  - bean 규약(getX) 명명 → 구현체는 Lombok `@Getter`만으로 계약 충족 (수동 오버라이드 없음)
- `CommonErrorCode` enum: `COMMON-400`(validation) / `COMMON-404` / `COMMON-500`
- `BusinessException`: ErrorCode + 선택적 detail(디버깅 보조, nullable — 없으면 응답 data가 null)
- **ApiResponse 2필드 규약 보강**: `isSuccess()` → `success()` 개명
  — is-getter는 Jackson이 응답에 `success` 필드를 추가해 code+data 2필드 규약을 깨뜨린다 (compose 검증에서 발견)

### 2. web-starter 실구현 (`starters/swtp-web-starter`)

- `GlobalExceptionHandler`(@RestControllerAdvice + `@Slf4j`, **자동구성 @Bean 등록** — 컴포넌트 스캔 아님):
  - `BusinessException` → ErrorCode의 HTTP 상태 + `ApiResponse(code, detail)` (detail 없으면 data null)
  - `MethodArgumentNotValidException` → 400 + data 슬롯에 `[{field, reason}]` 목록
  - 그 외 → 500 + data null, 상세는 서버 로그에만 (내부정보 비노출)
- `SwtpOpenApiAutoConfiguration`: `spring.application.name` 기반 공통 명세, `@ConditionalOnMissingBean` backoff
- 두 자동구성 모두 `@ConditionalOnWebApplication(SERVLET)` — gateway(WebFlux) 오염 방지
- webmvc/validation은 `compileOnly` — 스타터가 웹 스택을 강제 전파하지 않는다

### 3. kafka-starter Producer 실구현 (`starters/swtp-kafka-starter`)

- 의존성: **`spring-boot-starter-kafka`** (Boot 4 신설 스타터 — spring-kafka + Boot Kafka 자동구성 전파)
- `EventEnvelope<T>` record: `eventId`(UUID) / `eventType` / `source` / `occurredAt`(UTC) / `payload`
- `SwtpEventPublisher`: 발행 표준 진입점 — 항상 봉투로 감싸 발행, 파티션 키로 순서 보장
- 직렬화는 **EnvironmentPostProcessor로 프로퍼티 기본값 주입** (observability-starter와 동일 패턴):
  - `value-serializer` = `JacksonJsonSerializer` (**spring-kafka 4.x의 Jackson 3(tools.jackson) 기반** — R1 해소)
  - ProducerFactory를 재정의하지 않아 Boot의 전체 프로퍼티 표면(acks/retries/SSL)을 그대로 활용, 빈 충돌(R3) 원천 제거
  - `addLast` 주입 → 앱 yml/환경변수가 항상 이긴다

### 4. master-service 샘플 슬라이스 (`apps/master-service`)

- Flyway `V1__sample_item.sql`: 스키마명 하드코딩 없음 — `spring.flyway.default-schema=master`가 결정
  - 배선 규약: `flyway.schemas=master` + `default-schema=master` (history 테이블도 자기 스키마 내부)
  - `jpa.hibernate.ddl-auto=validate` — DDL은 Flyway 단독 소유, 불일치는 기동 실패로 드러난다
- `com.mo.swtp.master.sample` 패키지 (**전체 일회용** — 각 파일 헤더에 명시):
  - Entity/Repository/Service/Controller/DTO + 이벤트 2종
  - **이벤트 발행 2단계 분리**: Service는 트랜잭션 안에서 Spring 도메인 이벤트만 발행 →
    `SampleItemEventRelay`가 `@TransactionalEventListener(AFTER_COMMIT)`에서 `master.changed`로 중계
    (롤백된 변경의 이벤트가 Kafka로 새어나가지 않는다)
- API: `/api/master/sample-items` CRUD — gateway 기존 라우트(`/api/master/**`) 그대로 사용

### 5. Testcontainers 통합 테스트 (Phase 7+ 복제용 표준형)

- `AbstractIntegrationTest`: `@ServiceConnection`으로 접속 정보 자동 주입
  - PG: **운영 compose와 동일한 `timescale/timescaledb:2.26.1-pg17`** (`asCompatibleSubstituteFor`)
  - Kafka: `org.testcontainers.kafka.KafkaContainer` + `apache/kafka:4.3.1` (신형 클래스 — R2 해소)
- `SampleItemSliceIntegrationTest` 3건: CRUD 생명주기 + 이벤트 봉투 3종 컨슘 / 404 규약 / validation 규약
  - 응답 2필드 규약 회귀 방지 단언 포함 (`properties().hasSize(2)`)

### 6. 인프라 갱신

- `config-repo/application-docker.yml`: 컨테이너 네트워크 공통 접속 정보(datasource `timescaledb:5432`, kafka `kafka:9092`)
- compose: master-service `depends_on`에 timescaledb/kafka(healthy) + **kafka-init(completed)** 추가
  — 브로커 auto-create off이므로 토픽 생성 완료를 보장한 뒤 기동

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| `org.testcontainers:kafka` 좌표 미존재 | Boot 4.1 BOM의 Testcontainers는 **2.x** — 모듈명이 `testcontainers-kafka` 등으로 개편, `PostgreSQLContainer`도 비제네릭화 | 신규 좌표/API로 전환 |
| `TestRestTemplate` 패키지 미존재 | Boot 4 모듈 분해 — `spring-boot-resttestclient`의 `org.springframework.boot.resttestclient`로 이동, starter-test 미포함, 빈 등록도 `@AutoConfigureTestRestTemplate` opt-in으로 변경 | 의존성 2건(`resttestclient`, `restclient`) + 어노테이션 추가 |
| compose 검증에서 POST만 COMMON-500 | 앱 버그 아님 — Git Bash가 curl 인라인 한글 body를 CP949로 전달, 서버가 invalid UTF-8 거부 | UTF-8 파일 + `--data-binary @file`로 검증 (한글 round-trip 확인) |
| 응답에 `success` 필드 유출 | `isSuccess()`가 Jackson is-getter로 직렬화 | `success()`로 개명 + 통합 테스트에 2필드 단언 추가 |

## 검증 결과

```text
> gradlew build                                            BUILD SUCCESSFUL ✅
  (통합 테스트 3건 통과 — Testcontainers PG+Kafka, Docker Desktop 필요)
> docker compose up -d --build                             전 컨테이너 (healthy) ✅

[gateway 경유 CRUD]
POST   /api/master/sample-items          → 201 {"code":"SUCCESS","data":{"id":1,...}} ✅ (한글 저장 확인)
GET    /api/master/sample-items          → 200 목록 일치 ✅
PUT    /api/master/sample-items/1        → 200 수정 반영 ✅
DELETE /api/master/sample-items/1        → 200 ✅
GET    /api/master/sample-items/999      → 404 {"code":"COMMON-404",...} ✅
POST   (name 공백)                        → 400 {"code":"COMMON-400","data":[{"field":"name",...}]} ✅

[master.changed 컨슘] kafka-console-consumer --from-beginning --max-messages 3
sample-item.created / updated / deleted 봉투 3종 수신 ✅
  (eventId/eventType/source=master-service/occurredAt(ISO-8601)/payload 구조 확인)
```

## 결정 사항 및 알려진 한계

- **outbox 패턴 제외**: AFTER_COMMIT 발행은 커밋 직후 프로세스가 죽으면 이벤트가 유실될 수 있다.
  현 단계 요구는 "발행 사실 확인"이므로 수용하고, 실도메인 설계 시 소비자 정합성 요구와 함께 재평가한다.
- Testcontainers Kafka는 auto-create on(기본값)이라 토픽 사전 생성이 불필요 — 운영(auto-create off)과의 차이점.
- Git Bash에서 한글 JSON을 curl로 보낼 때는 반드시 UTF-8 파일 + `--data-binary` 사용 (검증 루틴 표준).

## 샘플 도메인 폐기 절차 (실제 master 설계 착수 시)

1. `com.mo.swtp.master.sample` 패키지 삭제
2. `V1__sample_item.sql`을 실제 설계의 V1으로 교체
3. `docker compose down -v`로 볼륨 리셋 — 개발 단계이므로 Flyway history 재작성 허용 (baseline 마이그레이션 불필요)
4. `SampleItemSliceIntegrationTest` 시나리오를 실도메인으로 교체 — **`AbstractIntegrationTest`는 그대로 유지**

### 실제 착수 시 이 절차에서 갈라진 지점 (2026-08-14)

실도메인 1차 DDL(태그·공정·시설·설비 6종)은 위 2번의 **V1 교체가 아니라 `V2__master_domain.sql` 추가**로 넣었다.

- 이유: 개발 DB(<internal-host>:15432)에 이미 V1이 적용돼 있어, 교체하면 Flyway 체크섬 불일치로 기동이 실패한다.
  V2 추가는 볼륨 리셋(3번) 없이 다음 부팅에서 그대로 적용된다.
- 따라서 **1·3·4번은 아직 남은 과제다** — `com.mo.swtp.master.sample` 패키지와 `SampleItemSliceIntegrationTest`,
  그리고 `sample_item` 테이블이 여전히 살아 있다. 실도메인 엔티티·API를 붙이는 시점에 함께 정리한다.
- V2에서 확정한 예외는 마이그레이션 파일 헤더에 기록했다:
  ERD 원본 유지를 위해 **FK 제약을 걸지 않았다**.

> **이후 변경 (Step 14)**: 위에서 "예외"로 적었던 `mdf_dttm`/`mdf_id` **NOT NULL**은 더 이상 예외가 아니다 —
> 플랫폼 감사 컬럼 규약 자체가 NOT NULL로 정정되어 V2의 4개 테이블은 `BaseEntity`를 그대로 상속하면 된다.
> [step-14-audit-mdf-not-null.md](step-14-audit-mdf-not-null.md) 참조.

## 다음 단계

Phase 7 — 수직 슬라이스 2: Telemetry (hypertable Flyway + kafka-starter Consumer 규약 + 조회 API + realtime SSE)
