# Step 08 — 수직 슬라이스 3: Job ("데이터가 스스로 흐르는 상태")

- 일자: 2026-08-12
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 8

## 목표

Phase 6(동기 API + 발행)·Phase 7(소비 → 적재 → SSE)에 이어, **스케줄이 데이터를 만들어 흘려보내는** 마지막 고리를 관통 검증한다:
job/batch/quartz 스키마 공식 DDL(Flyway), 자동/수동 동일 경로(202+executionId, Trigger Type), Quartz 1분 cron → Collector → `telemetry.raw`, Batch 집계(분→10분), `job.event` 발행.

> 결정 (2026-08-12):
> - **실행 이력·집계는 전부 샘플 일회용** — `com.mo.swtp.job.sample` 패키지 + `sample_` 테이블. 아키텍처 8장 설계(상태 5종·Trigger 4종)는
>   STARTING/RUNNING/COMPLETED/FAILED + SCHEDULED/MANUAL만 **패턴으로** 검증하고, 실설계는 차후 진행.
> - **집계 출력은 job 스키마 샘플 테이블** — telemetry 스키마 write 금지(소유권 준수), job-service 안에서 완결.
> - **데이터 접근은 JdbcClient** (`hikari.schema: job`) — 이력은 insert + 상태 update뿐이라 영속성 컨텍스트가 무용.
> - **Data Collector는 Job Service 내부 컴포넌트로 확정** — plan.md "문서 보완 필요 ①"(아키텍처 10장의 미결정) 해소.
>   실설계에서 PLC/SCADA/외부API 어댑터가 이 자리에 들어온다.

**영구 산출물**: Quartz/Batch 공식 메타 DDL(V1/V2), 3스키마 Flyway/tablePrefix 배선(yml), "자동/수동 동일 경로" 구조 패턴, compose depends_on 보강. 샘플 도메인만 버린다.

## 실행 내용

### 1. 의존성 확정 게이트 (`apps/job-service/build.gradle`)

- 추가: kafka-starter, `spring-boot-starter-jdbc`, `-quartz`, `-batch`, **`-batch-jdbc`**, `-validation`, `-flyway` + PG 런타임 2종, Testcontainers 표준 세트
- **`gradlew dependencies` + jar configuration-metadata로 실물 확정** (계획서 리스크 3의 이행):
  - quartz 2.5.2 / spring-batch-core 6.0.4 (Boot 4.1 BOM 관리)
  - `spring.batch.jdbc.initialize-schema`/`table-prefix`는 **spring-boot-batch-jdbc 모듈 소속** — Boot 4.x에서 Batch의
    JDBC JobRepository가 별도 스타터로 분리됐다 (batch 스타터만으로는 메타 이력이 DB에 남지 않는다)
  - `spring.batch.job.enabled`(자동 실행 스위치)는 기존 spring-boot-batch 소속 유지

### 2. Flyway 3분할 — 한 서비스가 3개 스키마를 소유하는 첫 사례

- `spring.flyway.schemas: job,batch,quartz` + `default-schema: job` (history는 job 스키마)
- **V1__quartz_schema.sql (영구)**: 의존 해석된 quartz-2.5.2.jar 실물에서 `tables_postgres.sql` 추출 →
  테이블 참조 위치(CREATE TABLE/REFERENCES/ON)만 `quartz.` 접두 정규화. 원본의 DROP 블록/말미 COMMIT은 마이그레이션 부적합으로 제거
- **V2__batch_schema.sql (영구)**: spring-batch-core-6.0.4.jar의 `schema-postgresql.sql` → 테이블 6종 + **시퀀스 3종까지** `batch.` 접두
  (JobRepository incrementer가 `table-prefix + "JOB_SEQ"` 문자열 조합으로 시퀀스를 찾는다)
- **V3__sample_job_tables.sql (일회용)**: `sample_job_execution`(이력) + `sample_collected_measurement`(자기 사본) + `sample_measurement_aggregate`(10분 버킷)
- **정규화 규약 확장**: "스키마명 하드코딩 금지"의 명시적 예외 — default-schema가 아닌 **프레임워크 소유 스키마** 대상 DDL은 접두 정규화한다
  (Phase 7의 extension 함수 `public.` 정규화와 같은 계열). 인덱스명 자체는 스키마 수식 불가라 접두 금지

### 3. 스키마 접근 배선 — hikari.schema 하나로는 부족한 첫 사례

| 대상 | 배선 | 원리 |
|---|---|---|
| JdbcClient (샘플 테이블) | `spring.datasource.hikari.schema: job` | 커넥션 기본 스키마 (Phase 7 규약) |
| Quartz 메타 | `org.quartz.jobStore.tablePrefix: quartz.QRTZ_` + PostgreSQLDelegate | Quartz SQL은 문자열 결합이라 스키마 수식 접두가 유효 |
| Batch 메타 | `spring.batch.jdbc.table-prefix: batch.BATCH_` | 동일 원리 — 시퀀스에도 적용됨 |

- 양쪽 모두 `initialize-schema: never` — DDL은 Flyway 단독 소유 (auto-init off)
- `spring.batch.job.enabled: false` — 기동 시 Batch 자동 실행 금지, 실행은 launcher 경유만
- `spring.quartz.overwrite-existing-jobs: true` — JDBC store에 남은 구 cron을 재기동 시 코드 정의로 갱신

### 4. 자동/수동 동일 경로 (`com.mo.swtp.job.sample`, 전부 일회용) — 아키텍처 8.5 패턴

```
Quartz Job(SCHEDULED) ─┐
                        ├→ SampleJobLauncher.launch(jobName, triggerType)   ← 단일 진입점 (비트랜잭션)
REST POST(MANUAL) ─────┘        │ ① create() 독립 트랜잭션: STARTING insert + STARTED 이벤트 → 커밋
                                │ ② runner.runAsync(executionId) 제출 (@Async — 커밋 후라 경합 없음)
                                └ ③ 즉시 반환 → 202 + executionId (아키텍처 8.6)
SampleJobRunner(@Async): markRunning → 본체 실행 → complete/fail   ← 전이마다 독립 트랜잭션
```

- **launcher가 비트랜잭션인 이유**: create()의 커밋이 끝난 뒤 비동기 제출해야 @Async 스레드가 커밋 전 이력 row를 못 보는 경합이 원천 차단된다
- 상태 전이(STARTED/COMPLETED/FAILED)는 master의 **2단 릴레이 복제**: 도메인 이벤트 → `@TransactionalEventListener(AFTER_COMMIT)` → `job.event` 발행 (key=executionId — 같은 실행의 전이 순서 보장)
- Quartz Job 클래스는 launcher 한 줄 위임체 — Boot의 SpringBeanJobFactory가 autowire하므로 **필드 주입** (생성자 주입 불가)
- cron은 `swtp.sample-job.*` 프로퍼티로 외부화 — 테스트가 재정의 (매분 수집 / 10분 집계, 아키텍처 8.3·8.4)

### 5. Collector + Batch 집계 — "발행자는 자기 사본을 가진다"

- `SampleCollector.collect()`: 고정 태그 3종 랜덤 값 → **자기 스키마 `sample_collected_measurement` insert와 같은 트랜잭션**에서
  도메인 이벤트 발행 → 릴레이(AFTER_COMMIT)가 `telemetry.raw`로 중계 (key=tagId)
  - payload 필드명(tagId/value/measuredAt)은 **telemetry 계약과 동일** — 이것이 E2E 성립 조건 (Consumer 규약: 계약은 JSON 구조로만)
  - 집계 입력을 telemetry 스키마 크로스 read로 하지 않는 이유: 소유권 회색지대 + 타 서비스 **샘플** 테이블 결합
- `sample-aggregate` Batch Job: 단일 Tasklet — `date_bin('10 minutes')` 전량 재집계 UPSERT 한 방 (멱등, 몇 번 돌려도 같은 결과)
  - chunk 지향(reader/writer)은 샘플 목적 대비 과대 — 대량 처리는 실설계(AI Dataset 등)에서 도입
  - JobParameters에 executionId — 매 실행 유니크(인스턴스 중복 방지) + 샘플 이력 ↔ Batch 메타 상호 참조 키
- Batch 실행은 runner 스레드에서 **동기** — `@BatchTaskExecutor` qualifier 빈이 없으면 Boot 기본이 동기라 ExitStatus 판정이 성립

### 6. REST (`/api/job/sample-executions` — gateway `/api/job/**` 라우트, StripPrefix 없음)

- `POST` `{jobName, requestedBy?}` → **202 Accepted** + STARTING 이력 (즉시 반환)
- `GET ?jobName=&limit=` 최근 이력 / `GET /{executionId}` 단건 (미존재 → COMMON-404)
- 미등록 jobName → COMMON-404. 집계 결과 조회 REST는 만들지 않는다 (판정 범위 외 — 검증은 psql/테스트)

### 7. Spring Batch 6 / Boot 4.1 API 세대 확정 (바이트코드 확인)

- **`JobLauncher`는 deprecated** — `JobOperator`(JobLauncher 상속)가 현행 실행 인터페이스, Boot 자동구성 빈도 JobOperator
- 패키지 이동: `Job`/`JobExecution` → `org.springframework.batch.core.job.*`, `JobParameters` → `core.job.parameters.*`,
  infrastructure 계열(RepeatStatus 등) → `org.springframework.batch.infrastructure.*`
- `@EnableBatchProcessing` 금지 — 붙이면 Boot 자동구성이 물러나 JDBC 배선(jdbc.* 프로퍼티)이 무시된다

### 8. 통합 테스트 (표준형 복제 3회차) + 인프라

- `AbstractIntegrationTest` 복제 (PG+Kafka, 동일 이미지). cron/scheduler-name은 서브클래스 `@TestPropertySource` 몫
- **컨텍스트별 Quartz 격리**: 프로퍼티가 다른 테스트 클래스 2개 = Spring 컨텍스트 2개 = 스케줄러 2개가 같은 quartz 스키마를 공유
  → **`spring.quartz.scheduler-name`을 클래스별로 분리** (Quartz의 모든 행이 SCHED_NAME 키잉이라 자연 격리, 비클러스터 경합 차단)
- `SampleJobSliceIntegrationTest` (cron 무력화 — 2099년): ① MANUAL E2E(202 → COMPLETED 폴링 → 자기 사본 + telemetry.raw payload 계약 +
  job.event STARTED→COMPLETED 순서·key=executionId) ② 집계(버킷 avg/min/max/count + **batch.BATCH_JOB_EXECUTION 메타 단언** — jdbc 배선 최종 증명)
  ③ 404 규약 ④ quartz.QRTZ_JOB_DETAILS 등록 단언 (Flyway V1 + tablePrefix 배선 증명)
- `SampleScheduledJobIntegrationTest` (cron 5초 + `@DirtiesContext(AFTER_CLASS)`): SCHEDULED·COMPLETED 이력 등장 폴링 — 자동/수동 동일 경로 증명.
  이력 단언은 반드시 triggerType 스코프 (DB는 static 공유)
- compose: job-service `depends_on`을 `<<: [*depends-config, *depends-db, *depends-kafka]`로 보강 (Phase 7 앵커 재사용)

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| `org.springframework.batch.repeat` 컴파일 실패 | Batch 6에서 infrastructure 계열이 `org.springframework.batch.infrastructure.*`로 이동 | jar 실물 확인 후 임포트 수정 (실행 내용 7) |

## 검증 결과

```text
> gradlew build                                            BUILD SUCCESSFUL ✅
  (job-service 통합 테스트 5건: 슬라이스 4 + SCHEDULED 1 — 첫 실행에 전부 통과, Testcontainers/Docker Desktop 필요)
> docker compose up -d --build                             전 컨테이너 (healthy) ✅
  (기존 DB 볼륨 위에서 Flyway V1~V3 3스키마 마이그레이션 통과 — job-service 30초 내 healthy)

[E2E — gateway 경유, 판정 3]
POST /api/job/sample-executions {"jobName":"sample-collect","requestedBy":"cli-admin"}
  → HTTP 202 + {executionId, triggerType: MANUAL, status: STARTING} 즉시 반환 ✅
GET .../{executionId} → COMPLETED (31ms 완주) ✅
GET /api/telemetry/sample-measurements?tagId=sample-job-tag-a&... → 적재 일치 ✅
  (수동 실행분 + Quartz 매분 cron 자동 발화분이 함께 조회됨 — 데이터가 스스로 흐르는 상태)
curl -N SSE: ":connected" + event:telemetry 9건 (3태그 × 수집 3회, Collector 발행 봉투 그대로) ✅

[SCHEDULED/MANUAL 구분 — 판정 4]
kafka CLI job.event: 실행별 STARTED→COMPLETED 전이가 key=executionId로 발행,
  triggerType MANUAL/SCHEDULED 각각 확인 ✅
GET /api/job/sample-executions → 두 Trigger Type 이력 공존 ✅

[집계 + 메타 배선]
POST sample-aggregate → COMPLETED (350ms) →
  job.sample_measurement_aggregate: 07:40 버킷 태그 3종 avg/min/max/sample_count=3 ✅
  batch.batch_job_execution: COMPLETED 1행 (table-prefix=batch.BATCH_ 배선 증명) ✅
  quartz.qrtz_job_details: 샘플 Job 2개 등록 (tablePrefix=quartz.QRTZ_ 배선 증명) ✅
```

## 결정 사항 및 알려진 한계

- **Trigger Type RETRY/RECOVERY, 상태 STOPPED(중지 API) 미구현** — 아키텍처 8.6/8.7의 나머지는 실설계에서
- **outbox 없음** (Phase 6 한계 승계) — 커밋 직후 프로세스 사망 시 job.event/telemetry.raw 유실 허용
- **집계는 전량 재집계** — 증분 윈도우(마지막 처리시각 기반)는 실설계 항목. 집계 결과 조회 REST 없음
- Collector의 실데이터 어댑터(PLC/SCADA/OPC) 없음 — 랜덤 생성. 어댑터 구조는 실설계에서
- Quartz misfire는 DoNothing(건너뛰기) — 밀린 발화 몰아치기 방지. 누락 재처리는 Batch 재처리(RECOVERY) 설계와 함께

## 샘플 도메인 폐기 절차 (실제 Job 설계 착수 시)

1. `com.mo.swtp.job.sample` 패키지 삭제
2. `V3__sample_job_tables.sql`만 실제 설계의 V3(이후)로 교체 — **V1(quartz)/V2(batch)는 영구 보존**
3. `docker compose down -v`로 볼륨 리셋 (개발 단계 — Flyway history 재작성 허용)
4. 통합 테스트 시나리오만 교체 — `AbstractIntegrationTest`·scheduler-name 격리 패턴은 유지
5. application.yml의 Quartz/Batch/Flyway 배선과 compose depends_on은 폐기 대상 아님

## 다음 단계

plan.md의 Phase 구성(0~8)이 완료된다. 이후는 실도메인 설계(master tag/telemetry tag_value/job 실이력) 또는 보완 항목(auth 슬라이스, Realtime 인증, DLT, outbox) 중 선택.
