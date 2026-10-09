# Step 04 — 인프라 docker-compose + observability-starter 실구현

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 4

> **후속 변경 (2026-08-21)** — 여기 기록된 Grafana 관측 스택(Alloy/Loki/Prometheus/Grafana)은
> 철거됐다. 고객사 요구사항 SFR-016이 ELK를 강제한다. 앱 계측(actuator·ECS 로그·requestId MDC)은
> 그대로 유지되며, ELK가 붙을 접점은 별도 문서에 명세돼 있다.
> → [step-16](step-16-observability-stack-teardown.md)

## 목표

미들웨어(데이터 계층 + 관측성 계층)를 단독 기동 가능한 compose로 구성하고,
앱 쪽 대응물인 **observability-starter를 실구현**하여 메트릭 파이프라인을 끝단까지 검증한다.

## 실행 내용

### 1. compose 스택 (`infrastructure/docker/compose.yaml`, 이미지 태그 2026-08 웹 검증 고정)

| 서비스 | 이미지 | 포트 | 비고 |
|---|---|---|---|
| timescaledb | timescale/timescaledb:2.26.1-pg17 | 5432 | init SQL로 **스키마 10개**만 생성 (테이블 DDL은 Flyway 소유) |
| kafka | apache/kafka:4.3.1 | 9092(호스트) | KRaft single-node, INTERNAL/HOST 리스너 분리(브로커 포트는 전 경로 9092 — HOST는 컨테이너 9094를 9092로 공개), **auto-create off** |
| kafka-init | apache/kafka:4.3.1 | - | `create-topics.sh`로 **토픽 8종** 명시 생성 (문서 9.3 구성안) 후 종료 |
| prometheus | prom/prometheus:v3.13.2 (LTS) | 9090 | 정적 scrape — Phase 4는 `host.docker.internal:포트` 기준, Phase 5에 컨테이너명 전환 |
| loki | grafana/loki:3.7.6 | 3100 | filesystem 저장, tsdb v13 스키마, 보존 7일 |
| alloy | grafana/alloy:v1.18.1 | 12345 | **Docker 소켓 discovery → 전 컨테이너 stdout 로그를 Loki로 push** |
| grafana | grafana/grafana:12.3.10 | 3000 | datasource(Prometheus/Loki) + dashboards 프로바이더 프로비저닝 |

- 토픽 8종: telemetry.raw / telemetry.minute / telemetry.aggregate / master.changed / prediction.generated / equipment.event / control.event / job.event (partitions 3, RF 1)
- 스키마 10개: master, telemetry, operation, ems, pms, auth, ai, job, batch, quartz
- `.gitattributes` 추가: 컨테이너 마운트 스크립트(`*.sh`, `*.alloy`)는 Windows checkout에서도 LF 유지

### 2. observability-starter 실구현 (Phase 2 골격 → 실구현)

- `api 'io.micrometer:micrometer-registry-prometheus'` — 앱은 스타터 추가만으로 scrape 대상이 됨
- `SwtpObservabilityEnvironmentPostProcessor` (spring.factories 등록) — **최저 우선순위 기본값** 주입:
  - `management.endpoints.web.exposure.include=health,info,prometheus,metrics`
  - `management.metrics.tags.application=${spring.application.name}` (Grafana 서비스 필터 기준)
  - 앱 application.yml/환경변수가 항상 우선하도록 `addLast` 배치 (우선순위 테스트 포함)
- **11개 앱 전부에 `swtp-observability-starter` 의존 추가**

### 3. 관련 결정 (사전 논의 반영)

- **Loki는 앱 의존성 0개가 정상**: 앱은 stdout만 → Alloy가 Docker discovery로 수집. `loki-logback-appender`(직접 push)는 Loki 장애가 앱에 전파되어 비채택
- **Eureka 클라이언트는 원안 유지**: 정적 라우팅으로 충분, 다중 서버 확장 시 도입 (Spring: eureka-client / Python: py-eureka-client)
- Grafana는 13.x 출시됐으나 계획 매트릭스(12.x 라인) 유지 — 12.3.10(최신 패치)
- kafka 데이터 볼륨 미사용(ephemeral) — 로컬 개발 단계 재기동 시 kafka-init가 토픽 재생성. 영속화는 Phase 8 전 재검토

## 검증 결과

```text
> gradlew build                                  BUILD SUCCESSFUL ✅ (starter 테스트 2건 포함)
> docker compose up -d                           전 컨테이너 기동 ✅
psql \dn                                         스키마 10개 + timescaledb 2.26.1 extension ✅
kafka-topics --list                              토픽 8종, kafka-init exit 0 ✅
Prometheus /-/ready 200, Loki /ready 200, Alloy /-/ready 200 ✅
Grafana /api/datasources                         Prometheus + Loki 2종 프로비저닝 ✅

[끝단 검증] master-service 호스트 기동 →
GET :8081/actuator/prometheus 200, application="master-service" 공통 태그 ✅
Prometheus 쿼리 up{job="swtp-apps",instance="host.docker.internal:8081"} = 1 (실제 scrape) ✅
Loki container 라벨 7종 수집 (Alloy Docker discovery 동작) ✅
```

## 다음 단계

Phase 5 — 앱 컨테이너화(공통 Dockerfile, layertools) + compose profiles 전체 스택 통합
(Optional: auth/autonomous/ems/pms/ai), healthcheck 체인, Prometheus 타깃 컨테이너명 전환
