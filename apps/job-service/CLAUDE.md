# job-service — Quartz + Batch 실행

## 역할 분담 (아키텍처 8장)

- **Quartz** = 실행 "시점" 결정. cron 트리거만 담당한다.
- **Spring Batch** = 집계·대량 처리·재처리 본체.
- **자동 실행과 수동 실행은 동일 경로를 탄다.** Quartz Job도 REST 요청도 `SampleJobLauncher`를 거치고, 이력의 `TriggerType`(SCHEDULED/MANUAL)만 다르다. 스케줄러 전용 우회로를 만들지 않는다.

## 스키마 3개 소유

`job`(업무) + `batch` + `quartz`(프레임워크 메타). 메타 DDL도 Flyway가 소유하므로 자동 초기화를 껐다.

- `spring.quartz.jdbc.initialize-schema: never` / `spring.batch.jdbc.initialize-schema: never`
- 메타 테이블 접근은 `search_path`가 아니라 각자 `tablePrefix`의 스키마 수식(`quartz.QRTZ_`, `batch.BATCH_`)으로 한다. Batch는 시퀀스 incrementer도 이 접두를 탄다.
- `spring.batch.job.enabled: false` — 기동 시 Batch Job 자동 실행 금지

**Quartz/Batch 버전을 올리면 메타 DDL이 바뀔 수 있다.** `gradlew dependencies`로 실제 해석된 버전을 확인하고 그 버전의 공식 DDL로 새 Flyway 마이그레이션을 추가한다.

## 영속성

JPA가 아니라 `JdbcClient`를 쓴다 — 실행 이력은 insert + 상태 update뿐이라 영속성 컨텍스트가 무용하다. SQL에 스키마명을 하드코딩하지 않는다(`search_path`가 결정).

**상태 전이는 전이마다 독립 트랜잭션이다.** 실행 본체가 비동기 스레드에서 오래 돌아 하나로 묶을 수 없고, 각 전이가 커밋 확정되어야 `AFTER_COMMIT` 릴레이가 `job.event`를 발행한다.
