# Step 07 — 수직 슬라이스 2: Telemetry (시계열 파이프라인 표준형)

- 일자: 2026-08-12
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 7

## 목표

Phase 6("동기 API + 이벤트 발행")의 대칭인 **"Kafka 소비 → hypertable 적재 → 조회 API → SSE 브로드캐스트"**를 관통 검증한다.
**샘플 도메인(`sample_measurement`)은 일회용**이고, 영구 산출물은 재사용 패턴이다:
kafka-starter Consumer 규약, hypertable Flyway 규약, JdbcClient 시계열 접근 패턴,
realtime SSE 브로드캐스트 골격(영구 — 폐기 대상 아님), Testcontainers 표준형 복제 2회차.

> 결정 (2026-08-12): 실제 `tag_value` 설계는 차후 진행 — 임의 샘플 테이블 1개로 파이프라인만 검증한다.
> 데이터 접근은 JPA가 아닌 **JdbcClient** — append-only 시계열에 영속성 컨텍스트가 무용하고,
> 재소비 멱등(ON CONFLICT DO NOTHING)에 native SQL이 자연스럽다.

## 실행 내용

### 1. kafka-starter Consumer 규약 (`starters/swtp-kafka-starter`)

- **"문자열 수신 + 명시적 파싱" 규약**: consumer 역직렬화 기본값은 String —
  JacksonJsonDeserializer의 `__TypeId__` 헤더 방식은 소비측이 발행측 클래스명에 결합되므로 배제.
  서비스 간 계약은 JSON 구조로만 유지하고, 소비측이 `EventEnvelopeParser`로 payload 타입을 지정해 해석한다
- EnvironmentPostProcessor 기본값 추가 (최저 우선순위 — 앱 yml이 이긴다):
  - `consumer.group-id` = `${spring.application.name}` (placeholder는 조회 시점 해석 — 서비스별 독립 그룹)
  - `consumer.auto-offset-reset` = `earliest` (적재 파이프라인 안전 기본값 — realtime이 latest로 재정의)
- `EventEnvelopeParser`: JsonMapper 기반 `parse(json, payloadType)` → `EventEnvelope<T>`, 자동구성 @Bean
- build.gradle: `api 'tools.jackson.core:jackson-databind'` 추가 — spring-kafka는 Jackson을 optional로 두므로
  직렬화 기본값(JacksonJsonSerializer)과 파서가 요구하는 의존을 스타터가 명시 전파
- 에러 처리는 Boot 기본(DefaultErrorHandler — 재시도 후 skip + 로그) 수용. **DLT 없음** — 실도메인에서 재평가

### 2. EnvironmentPostProcessor 신 인터페이스 이관 (kafka + observability 스타터)

- Boot 4.1에서 `org.springframework.boot.env.EnvironmentPostProcessor`가 **deprecated(removal)** —
  신 위치는 `org.springframework.boot.EnvironmentPostProcessor` (시그니처 동일)
- spring.factories 키도 신 인터페이스 FQN으로 교체 — Boot은 신 키를 기본 로드하고 구 키는 하위호환 경로

### 3. telemetry-service 샘플 슬라이스 (`apps/telemetry-service`)

- Flyway `V1__sample_measurement.sql`: `sample_measurement(measured_at, tag_id, value)` + PK(tag_id, measured_at)
  - **hypertable 규약**: PK는 파티션 컬럼 포함 필수, chunk interval 7일(문서 12.3),
    `public.create_hypertable(..., public.by_range(...))` — **extension 함수는 public. 정규화 필수** (아래 문제 2)
- `com.mo.swtp.telemetry.sample` 패키지 (**전체 일회용**): payload 계약 record + JdbcClient 리포지토리 +
  `@KafkaListener` 컨슈머(그룹/오프셋은 스타터 기본값) + 조회 API 2종
  - 적재는 `ON CONFLICT (tag_id, measured_at) DO NOTHING` — Kafka 재전달 멱등
  - API: GET `/api/telemetry/sample-measurements?tagId&from&to`(기간) / `.../{tagId}/latest`(현재값, 없으면 COMMON-404)
- 스키마 지정은 SQL 하드코딩 없이 **`spring.datasource.hikari.schema: telemetry`** — 커넥션 기본 스키마로 해결
  (docker 공통 yml과 키가 달라 컨테이너에서도 유효)

### 4. realtime-service SSE 최소 브로드캐스트 (`apps/realtime-service`) — 영구 골격

- `com.mo.swtp.realtime.stream` (샘플 아님): `TelemetryStreamBroadcaster`(SseEmitter 레지스트리, 타임아웃 30분,
  끊긴 구독자 자동 제거) + `TelemetryStreamConsumer`(telemetry.raw → **봉투 JSON 파싱 없이 그대로** 브로드캐스트 —
  payload 스키마 비결합이라 샘플 폐기의 영향 없음) + GET `/api/realtime/telemetry/stream`
- 구독 직후 SSE 주석(`:connected`) 1줄 전송 — SseEmitter는 첫 전송 전까지 응답 헤더를 flush하지 않아
  클라이언트가 연결 성공(onopen)을 알 수 없는 문제 방지
- `consumer.auto-offset-reset: latest` 재정의 — 브로드캐스트는 과거 재생 불필요 ("앱 yml이 스타터 기본값을 이긴다" 검증 사례)
- 범위 제외: 태그별 필터링(전체 브로드캐스트만), 인증(auth 슬라이스에서 결정), WebSocket(SSE만)

### 5. web-starter 보강 (영구 개선)

- `GlobalExceptionHandler`에 `AsyncRequestTimeoutException` 전용 핸들러 추가 —
  SSE 등 비동기 요청 타임아웃은 정상 수명주기인데 catch-all이 ERROR 로그 + 커밋된 스트림에 ApiResponse 쓰기를
  시도하던 노이즈 제거. 커밋됐으면 무기록, 미커밋이면 `COMMON-503`(CommonErrorCode에 SERVICE_UNAVAILABLE 추가)

### 6. 통합 테스트 (표준형 복제 2회차)

- telemetry: `AbstractIntegrationTest`(PG+Kafka, Phase 6 동일 이미지) + 슬라이스 테스트 3건 —
  발행→적재→기간/현재값 조회 일치 + 재발행 멱등 + 404 규약 + **hypertable 실동작 단언**(`timescaledb_information.hypertables`)
- realtime: Kafka 단독 간소판 + SSE 테스트 1건 — `ContainerTestUtils.waitForAssignment`로
  리스너 할당 대기(latest 오프셋의 발행 유실 경합 방지) 후 발행 → data 라인 수신 단언

### 7. 인프라 갱신

- compose depends_on 앵커 승격(Phase 6 예고 사항): `x-depends-kafka` + `x-depends-db` 정의,
  `<<: [*depends-config, *depends-db, *depends-kafka]` 머지로 master/telemetry 적용, realtime은 DB 제외

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| kafka-starter 컴파일 실패 (tools.jackson 미존재) | spring-kafka는 Jackson **optional** 의존 — 스타터 classpath에 없었음 | 스타터가 `api`로 명시 전파 (직렬화 기본값도 런타임에 요구) |
| EnvironmentPostProcessor removal 경고 | Boot 4.1이 `boot.env` 패키지를 deprecated — 신 위치 `org.springframework.boot` | 두 스타터 인터페이스/spring.factories 키 이관 (바이트코드로 신·구 로딩 경로 확인) |
| SSE 테스트: 연결 대기에서 타임아웃 | SseEmitter는 **첫 이벤트 전송 때 응답 헤더 flush** — 발행 전 헤더 대기는 영원히 안 옴 | 테스트는 서버 측 신호(구독자 수)로 등록 확인 후 발행. 운영은 구독 직후 `:connected` 주석 전송으로 해결 |
| compose에서 Flyway `by_range` 미존재 (TC는 통과) | hikari.schema가 커넥션 search_path를 telemetry로 교체 → Flyway 마이그레이션 커넥션에서 **public의 extension 함수가 비가시** | extension 함수 `public.` 정규화 — **hypertable 마이그레이션 규약**으로 채택 (TC/compose 모두 검증) |
| catch-all의 AsyncRequestTimeoutException ERROR 노이즈 | SSE 타임아웃(정상 수명주기)이 500 처리 경로로 유입 | 전용 핸들러 + COMMON-503 (실행 내용 5) |

## 검증 결과

```text
> gradlew build                                            BUILD SUCCESSFUL ✅
  (kafka-starter 6건 + telemetry 슬라이스 3건 + realtime SSE 1건 — Testcontainers, Docker Desktop 필요)
> docker compose up -d --build                             전 컨테이너 (healthy) ✅

[kafka CLI 발행 → 적재 → gateway 경유 조회]  * UTF-8 파일 + MSYS_NO_PATHCONV=1 (Phase 6 검증 루틴 표준)
kafka-console-producer → telemetry.raw 봉투 2건(cli-0001/0002, turbidity-001)
GET /api/telemetry/sample-measurements?tagId=turbidity-001&from=...&to=...
  → {"code":"SUCCESS","data":[{...0.42...},{...0.55...}]} 발행 내용과 일치 ✅
GET /api/telemetry/sample-measurements/turbidity-001/latest → 최신(0.55) ✅
GET .../no-such/latest → 404 + COMMON-404 ✅

[curl -N SSE — gateway 경유]
:connected 주석 즉시 수신(연결 확인) → 발행 2건이 event:telemetry + data:{봉투 JSON 그대로}로 수신 ✅
  (R4 gateway WebFlux 스트리밍 통과 검증 겸함)

[hypertable]
timescaledb_information.hypertables → telemetry.sample_measurement ✅ / chunks 1개 생성 ✅
로컬 실측: SSE 연결 50초 유지(30초 컷 없음 — emitter 타임아웃 30분 정상 적용)
```

## 결정 사항 및 알려진 한계

- **DLT(Dead Letter Topic) 없음**: 파싱 실패/적재 실패 메시지는 재시도 후 skip + 로그로만 남는다. 실도메인 설계 시 재평가
- **JdbcClient는 기동 시 스키마 검증 없음** (JPA ddl-auto=validate 부재) — 엔티티/스키마 불일치는 쿼리 실패로 드러난다
- SSE는 전체 브로드캐스트 + 무인증 — 태그 필터링은 실도메인, 인증은 auth 슬라이스(문서 보완 항목)에서 결정
- 통합 테스트가 로컬 compose config-server(8888)에 붙을 수 있다 — @ServiceConnection이 datasource/kafka를
  덮어쓰므로 무해하지만, config-repo에 앱 동작을 바꾸는 키를 넣을 때는 유의

## 샘플 도메인 폐기 절차 (실제 telemetry 설계 착수 시)

1. `com.mo.swtp.telemetry.sample` 패키지 삭제
2. `V1__sample_measurement.sql`을 실제 설계(tag_value 등)의 V1으로 교체 — **public. 정규화 규약 유지**
3. `docker compose down -v`로 볼륨 리셋 (개발 단계 — Flyway history 재작성 허용)
4. `SampleTelemetrySliceIntegrationTest` 시나리오만 교체 — `AbstractIntegrationTest`는 유지
5. **realtime-service는 폐기 대상 아님** — 봉투 pass-through라 payload 스키마 변경의 영향 없음

## 다음 단계

Phase 8 — 수직 슬라이스 3: Job (job/batch/quartz 스키마 + JobExecutionService + Quartz cron → Collector → `telemetry.raw` + Batch 집계 + `job.event`)
