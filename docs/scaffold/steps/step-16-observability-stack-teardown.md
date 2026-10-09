# Step 16 — 관측 스택 철거와 ELK 전환 준비

- 일자: 2026-08-21
- 상태: ✅ 완료 (철거 + 접점 명세. ELK 도입은 2사이클 별도 진행)
- 관련: [step-04-infrastructure.md](step-04-infrastructure.md), [step-09-observability-persistence.md](step-09-observability-persistence.md), [step-13-log-format-switch.md](step-13-log-format-switch.md), 아키텍처 14장·15.4

## 발단

고객사 요구사항 **SFR-016 (AI 플랫폼 서버 내부 프로세스 모니터링)** 이 ELK를 강제한다.

> AI 플랫폼 서버에서 실행되는 프로세스 상태 모니터링
> ◦ 정수장 별 배포된 도커 내부 프로세스가 정상 동작하는지 모니터링
> - 도커 내부 프로세스에 대한 운영 정보를 취합하여 정상 동작 및 장애 상황에 대한 모니터링 기능 제공
> - 알고리즘 모델 프로세스 동작 상태 정보 제공
> - **ELK 기반** AI 플랫폼 서버 상태 정보 제공

현 관측 스택은 Phase 4·5·9에서 결정한 Alloy / Loki / Prometheus / Grafana다. 교체가 불가피하다.

작업 착수 전 결합도를 조사한 결과, **관측 스택이 애플리케이션에 거의 침투하지 않았다**는 사실이 드러났다. 이것이 아래 결정의 근거다.

1. **Grafana 전용 결합점은 인프라 디렉토리 4개 + compose 블록 1개가 사실상 전부다.** Prometheus는 pull(scrape), Alloy는 `docker.sock` discovery라 앱 정의에 흔적이 없다. 앱 서비스 중 관측 컨테이너에 `depends_on`을 건 것은 0개, compose에 `logging:` 드라이버 설정도 없다.
2. **커스텀 계측이 리포 전체에 0건이다.** `MeterRegistry` 주입, `@Timed`, `@Counted`, `ObservationRegistry`, `Tracer` 사용처가 없다. actuator 자동 메트릭만 쓴다.
3. **`swtp-logback-ecs.xml`의 ECS는 Elastic Common Schema다.** Boot 4 내장 `StructuredLogEncoder`가 외부 라이브러리 0개로 생성한다. 즉 로그 경로는 **앱 변경 0줄로 ELK에 붙는다.** step-04·step-09가 `loki-logback-appender`·`logstash-logback-encoder`를 의도적으로 비채택한 결정이 여기서 배당금을 냈다.

## 결정 (2026-08-21)

### 1. Grafana 런타임만 제거하고 계측 파사드는 전부 유지한다

관측은 **벤더 런타임 / 벤더 익스포터 / 중립 파사드** 3층으로 나뉜다. 이번에 걷어내는 것은 1층뿐이다. 파사드까지 걷어내면 2사이클이 *익스포터 교체*가 아니라 *계측 재설계*가 되어 비용이 몇 배가 된다.

### 2. `micrometer-registry-prometheus`를 남긴다

`/actuator/prometheus`는 Prometheus 전용이 아니라 **OpenMetrics 표준 엔드포인트**다. Elastic Agent / Metricbeat의 `prometheus` 모듈이 그대로 긁는다. 제거하면 2사이클에서 되살려야 하므로 남긴다. 함께 `config-repo/application.yml`의 `management` 블록과 `SwtpObservabilityEnvironmentPostProcessor` 기본값도 **무수정**이다.

### 3. ELK 도입을 별도 사이클로 분리한다

고객사 미확정 사항(배치 형태, SFR-016 구현 한계, ai-service 계약)이 남아 있어 지금 짓기 시작하면 두 번 짓게 된다. 이 문서의 "ELK 접점 명세"가 2사이클의 입력이 된다.

### 4. 아키텍처 문서 14장 본문은 재작성하지 않는다

ELK 구성이 확정되기 전에 쓰면 두 번 쓴다. 이번에는 전환 예정 배너만 달고, 본문 재작성은 2사이클에 둔다. Phase 문서(step-04/05/09)와 plan.md도 **이력이므로 삭제하지 않고** 역참조 배너만 추가한다 — step-09에 step-13 배너를 넣은 선례를 따랐다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `infrastructure/alloy/` | **디렉토리 삭제** (`config.alloy`) |
| `infrastructure/loki/` | **디렉토리 삭제** (`loki-config.yaml`) |
| `infrastructure/prometheus/` | **디렉토리 삭제** (`prometheus-back.yml`, 고아 파일 `prometheus.yml`) |
| `infrastructure/grafana/` | **디렉토리 삭제** (provisioning 전체 + `jvm-overview.json`) |
| `infrastructure/docker/compose.yaml` | **관측성 계층 블록(서비스 4개) 제거**, **named volume 4개 제거**(`prometheus-data`/`loki-data`/`grafana-data`/`alloy-data`). `SWTP_LOG_FORMAT` 전달과 `../../logs` bind mount는 유지, 주석의 벤더 고유명만 중립화 |
| `infrastructure/CLAUDE.md` | "볼륨과 로그 경로" 섹션 — stdout 단독 수집 계약을 벤더 중립 문장으로 재작성, 관측 백엔드 부재 명시 |
| `infrastructure/docker/.env.example` | `SWTP_LOG_FORMAT` 설명에서 Alloy/Loki/Grafana 언급 제거, ECS를 Elastic Common Schema로 명시 |
| `.gitattributes` | `*.alloy text eol=lf` 제거 |
| `README.md` | 기술 스택 표 Observability 행, 디렉토리 트리 주석 |
| `docs/scaffold/plan.md` | 상단 후속 변경 배너 + 버전 매트릭스 Observability 행 취소선 (**Phase 4·5·9 본문은 이력으로 보존**) |
| `docs/scaffold/steps/step-{04,05,09}` | 역참조 배너 추가 (본문 무수정) |
| `docs/architecture/스마트정수장_리빌드_아키텍처.md` | 14장·15.4에 전환 예정 배너 (본문 재작성은 2사이클) |
| 앱·스타터·`libs`·`build-logic`·`config-repo`·`gradle/libs.versions.toml` | **변경 없음** |

---

## ELK 접점 명세 (2사이클 입력)

### 로그

| 항목 | 값 |
|---|---|
| 진입점 | **컨테이너 stdout 단독.** 파일 로그(`logs/<service>/`)는 감사·장애분석용 백업이며 수집기가 읽지 않는다(이중 적재 방지) |
| 포맷 스위치 | `SWTP_LOG_FORMAT` = `plain`(기본) \| `ecs`. `config-repo/application.yml`의 `logging.config: classpath:swtp-logback-${SWTP_LOG_FORMAT:plain}.xml`가 SSOT |
| 생성 경로 | Boot 4 내장 `StructuredLogEncoder` (`CONSOLE_LOG_STRUCTURED_FORMAT=ecs`). **외부 라이브러리 의존 0** |
| 현재 필드 | `@timestamp`, `log.level`, `log.logger`, `service.name`, `service.version`, `service.node.name`(미설정), `process.pid`, `process.thread.name`, `message`, `ecs.version`(8.11), `requestId` |
| 게이트웨이 접근 로그 | logger `swtp.gateway.access` — `http.method`, `http.path`, `http.status`, `gateway.route`, `gateway.target`, `duration.ms`, `signal` (SLF4J fluent `addKeyValue`) |
| 상관관계 | `X-Request-Id` (`SwtpHeaders.REQUEST_ID`) → MDC 키 `requestId`. 게이트웨이 발급, 전 구간 전파. **분산 트레이싱은 미도입**(step-09 결정) |
| 파일 로그 | `SWTP_LOG_FORMAT` 값과 무관하게 항상 평문. 일반/에러 분리, 100MB·30일·3GB 롤링 |

**수집기가 대신 해야 할 일 (철거된 Alloy가 하던 것):**

1. Docker discovery (`unix:///var/run/docker.sock`, 읽기 전용 마운트)
2. 컨테이너명 `/(swtp-.*)` 정규식 필터 — 호스트의 무관한 컨테이너 로그 유입 차단
3. ECS JSON 파싱 → 필드 색인
4. 수집 위치(positions) 영속화 — 재시작 시 중복/누락 방지 (Alloy는 `alloy-data` 볼륨을 썼다)

> **참고**: Alloy는 `level`/`service_name`만 라벨로 승격하고 `request_id`는 본문에만 뒀다 — 요청마다 값이 달라 라벨로 올리면 스트림이 폭발하기 때문이다. Elasticsearch는 라벨/스트림 모델이 아니라 문서 색인이므로 **이 제약은 사라진다.** `requestId`를 그대로 검색 가능한 필드로 두면 된다. Loki 대비 ELK의 실질적 이점이다.

### 메트릭

| 항목 | 값 |
|---|---|
| 엔드포인트 | `/actuator/prometheus` (OpenMetrics 표준) — Metricbeat `prometheus` 모듈이 그대로 수집 |
| 노출 설정 | `config-repo/application.yml`의 `management.endpoints.web.exposure.include` |
| 공통 태그 | `management.metrics.tags.application = ${spring.application.name}` |
| 커스텀 메트릭 | **없음.** actuator 자동 메트릭만 (`jvm_*`, `process_cpu_usage`, `http_server_requests_seconds_*`) |
| 인증 | 게이트웨이 `permit-all-paths`에 `/actuator/**`, `/*-service/actuator/**` — 토큰 없이 접근 가능 |

**scrape 타깃 목록** (삭제된 `prometheus-back.yml`에서 이관):

| 구분 | 서비스:포트 |
|---|---|
| Core (항상 기동) | `gateway:8080`, `config-server:8888`, `master-service:8081`, `telemetry-service:8082`, `realtime-service:8083`, `job-service:8084` |
| Optional (profile 기동) | `discovery-server:8761`, `auth-service:8085`, `autonomous-service:8086`, `ems-service:8087`, `pms-service:8088` |
| AI | `ai-service:8000` — **Gradle 모듈이 아니라 위 계약 밖이다.** 아래 미결 3 참조 |

### 헬스

`/actuator/health` + compose의 healthcheck 체인(`depends_on: service_healthy`). 이미 전 앱에 구성돼 있어 **신규 작업 없이 SFR-016의 "정상 동작 모니터링"에 그대로 쓸 수 있다.**

### 보존한 대시보드 쿼리 (Kibana 재작성 시 참조)

삭제된 `jvm-overview.json` (uid `swtp-jvm`)의 패널 4종. 템플릿 변수 `application` = `label_values(jvm_memory_used_bytes, application)`.

| 패널 | 쿼리 |
|---|---|
| JVM Heap | `sum(jvm_memory_used_bytes{application="$application", area="heap"})` / `sum(jvm_memory_max_bytes{...})` |
| CPU 사용률 | `process_cpu_usage{application="$application"}` / `system_cpu_usage{...}` |
| HTTP 요청 처리율 | `sum by (status) (rate(http_server_requests_seconds_count{application="$application"}[1m]))` |
| 컨테이너 로그 | LogQL `{container="swtp-$application"}` |

---

## SFR-016 구현 한계

요구 문구를 우리 아키텍처 용어로 번역하며 경계를 박는다. **2사이클 착수 전 고객사와 합의가 필요하다.**

| 요구 문구 | 구현 한계 | 근거 |
|---|---|---|
| "도커 내부 프로세스가 정상 동작하는지" | **컨테이너 = 프로세스로 정의한다.** 컨테이너 상태(up/exited/restarting) + actuator health까지. 컨테이너 내부 OS 프로세스 트리 열거는 범위 밖 | 전 컨테이너가 단일 프로세스(Java 1 / Python 1). 내부 프로세스 열거는 정보량이 0이면서 수집 비용만 발생 |
| "운영 정보를 취합하여 정상/장애 모니터링" | Metricbeat `docker` 모듈 + `/actuator/prometheus` + 기존 healthcheck 체인 | 신규 계측 없이 기존 자산으로 충족. 커스텀 메트릭이 0건이라 늘릴 이유가 없다 |
| "알고리즘 모델 프로세스 동작 상태 정보 제공" | **`ai-service`가 보고하는 3개 신호** — 프로세스 생존 / 마지막 추론 시각 / 추론 에러율. 모델 정확도·드리프트·재학습 상태는 범위 밖 | 유일한 실질 신규 작업 (아래 미결 3) |
| "ELK 기반 상태 정보 제공" | **Kibana 대시보드로 제공한다.** 플랫폼 자체 화면 임베드·자체 API 재노출은 별도 요구로 분리 | 최대 스코프 리스크. "제공"을 UI 통합으로 읽으면 작업량이 수 배가 된다 |

## 미결 사항 (2사이클에서 결정)

1. **정수장 식별 필드 — 해법 검증 완료, 적용 시점만 남았다.**
   현 로그에는 사이트 식별자가 비어 있다. 중앙 집계로 전환하는 순간 "어느 정수장 로그인가"를 구분할 수 없고, **나중에 추가하면 적재된 인덱스 재색인 + 대시보드 쿼리 전면 수정**이 따라온다. 인프라 배치는 되돌리기 쉽지만 데이터 스키마는 그렇지 않다 — **ELK 도입 1일차에 넣어야 한다.**

   **해법이 이미 스키마 안에 있다.** ECS는 노드 식별 슬롯 `service.node.name`을 정의하고, Boot 4가 이를 `logging.structured.ecs.service.node-name` 프로퍼티로 노출한다. 현재 로그에 `"service":{"node":{}}`가 **빈 객체로 출력되고 있다** — 슬롯은 이미 나가고 있고 값만 비어 있다.

   실동작 검증 (2026-08-21):
   ```
   java -jar discovery-server.jar --logging.structured.ecs.service.node-name=PLANT-GANGNEUNG-01
   → {"service":{"name":"discovery-server","node":{"name":"PLANT-GANGNEUNG-01"}}, ...}
   ```

   즉 **커스텀 필드 신설도, 코드 변경도 필요 없다.** `config-repo/application.yml`에 `logging.structured.ecs.service.node-name: ${SWTP_SITE_CODE:}` 한 줄을 넣고 정수장별 `.env`에 `SWTP_SITE_CODE`를 적으면 된다 — `SWTP_LOG_FORMAT`·`SWTP_AUTH_MODE`와 같은 축이다. 남은 결정은 **코드 체계**(정수장 코드 명명 규칙)와 `SwtpHeaders`에 상수로 올릴지 여부뿐이다.

2. **ELK 배치 — 우선 정수장 서버 동거, 중앙 전송 전환을 전제로 짓는다.**
   전환 비용을 0에 수렴시키는 방법은 두 가지다.
   - 로컬 Elasticsearch/Kibana를 **`elk` compose profile로 격리** → 중앙 전송 사이트는 profile을 켜지 않으면 컨테이너가 뜨지 않는다. Optional 서비스를 profile로 분리하는 기존 규약(`auth`/`autonomous`/`pms`/`ai`/`full`)과 같은 축이다.
   - 수집 에이전트의 output을 **`SWTP_ELASTIC_HOSTS` 환경변수로** (기본값 `http://elasticsearch:9200`) → 중앙이면 외부 URL로 값만 교체. 정수장별 차이를 환경변수 한 줄로 흡수하는 확립된 패턴이다.

   이렇게 지으면 사이트별 상이 구성은 **profile 토글 + 값 한 줄**이 되고 코드 변경이 0이다.
   단, Elasticsearch 힙·디스크 요구가 크므로 **동거 시 단일 서버 리소스 계획을 다시 산정해야 한다.**

3. **`ai-service` 로그 계약 — 실질 신규 작업은 여기 하나다.**
   `ai/ai-service`는 Gradle 모듈이 아니라 `swtp-observability-starter`를 쓸 수 없다. Java 앱 11개는 ECS JSON을 공짜로 뱉지만 **Python 쪽에는 아무 약속이 없다.** 여기서 필드명이 어긋나면 Kibana에서 서비스 간 상관관계가 끊긴다. 최소한 `log.level` / `service.name` / `requestId` 3개 필드를 같은 이름으로 맞추는 계약이 필요하다.

4. **SFR-016 구현 한계 4개 경계선** — 위 표. 고객사 합의 필요.

## 함정 기록

1. **Prometheus가 프로젝트 scrape 설정을 읽은 적이 없다.**
   `compose.yaml`이 `../prometheus/prometheus-back.yml`을 `/etc/prometheus/prometheus-back.yml`에 마운트하면서 `command:`로 `--config.file`을 지정하지 않았다. `prom/prometheus` 이미지의 기본 인자는 `--config.file=/etc/prometheus/prometheus.yml`이므로, 실제로 로드된 것은 이미지 내장 기본 설정(자기 자신만 scrape)이다. **앱 타깃 11개가 등록된 적이 없다.** Grafana JVM 대시보드가 비어 보였다면 원인이 이것이다.
   → 2사이클에서 Metricbeat/Filebeat 설정을 마운트할 때 **마운트 경로와 이미지가 실제로 읽는 경로가 같은지** 반드시 확인한다. 마운트는 성공하고 기동도 성공하므로 조용히 실패한다.

2. **`config-server`만 prometheus 엔드포인트가 닫혀 있는데 scrape 타깃에는 들어 있었다.**
   `apps/config-server/src/main/resources/application.yml`의 `include: busrefresh, refresh, health`. config-server는 `spring.config.import`가 없어 자기가 서빙하는 config-repo를 자기는 읽지 않으므로 이 값이 최종값이다. 2사이클에서 config-server 메트릭이 필요하면 이 리스트에 `prometheus`를 더해야 한다.

3. **`config-repo`의 `exposure.include`는 스타터 기본값을 병합이 아니라 치환한다.**
   스타터 기본값은 `health,info,prometheus,metrics`인데 `config-repo/application.yml`이 `[health, env, busrefresh, prometheus, mappings]`로 덮는다. 원격 설정이 붙은 환경에서는 `info`·`metrics`가 사라지고, 단독 기동 시에는 반대가 된다. **두 환경의 actuator 노출 목록이 다르다.** 리스트형 프로퍼티가 치환된다는 함정은 `config-repo/CLAUDE.md`가 `swtp.security.permit-all-paths` 맥락에서만 경고하고 있어 management 쪽에는 같은 주의가 없다.

4. **ECS JSON은 중첩 객체다 — 점 표기 평면 키가 아니다.**
   wire 상의 실제 출력은 `{"log":{"level":"WARN"},"service":{"name":"gateway"}}`이지 `{"log.level":"WARN"}`이 아니다. Elasticsearch가 색인할 때 이를 `log.level`·`service.name` 점 표기 필드로 평탄화하므로 **Kibana에서 보이는 이름과 JSON 구조가 다르다.** 삭제된 Alloy의 `stage.json`이 `log.level`을 쓴 것도 평면 키가 아니라 경로 표현식이었다. Filebeat 매핑이나 ingest pipeline을 쓸 때 이 차이를 혼동하면 필드가 조용히 비어 나온다.

## 알려진 한계

1. **이 사이클 완료 시점에 플랫폼은 관측 백엔드가 없는 상태다.**
   로그는 stdout으로 계속 나오고 `/actuator/*`도 살아 있으나, 수집·저장·조회 수단이 없다. 2사이클 전까지 로그 조회는 `docker compose logs`다. 이는 의도된 중간 상태이며, ELK 구성이 확정되기 전에 임시 백엔드를 세우는 비용을 피하기 위한 선택이다.
2. **아키텍처 문서 14장 본문은 재작성하지 않고 배너만 달았다.** ELK 구성 확정 후 2사이클에서 재작성한다.
3. **`git`에서 삭제됐을 뿐 Docker named volume은 남는다.** 아래 검증 절의 수동 정리가 필요하다.

## 검증

앱·스타터 코드를 건드리지 않았으므로 **빌드는 무수정 통과해야 한다.** 통과하지 않으면 범위를 넘은 것이다.

```bash
# 1) 빌드 — 관측 스타터 테스트 3종 포함 전부 통과해야 정상
./gradlew build

# 2) compose 문법 + 관측 서비스 부재
docker compose -f infrastructure/docker/compose.yaml config --services

# 3) 기동 — 전 컨테이너 healthy
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml up -d --build
docker compose -f infrastructure/docker/compose.yaml ps

# 4) ELK 접점 생존 확인 (2사이클이 붙을 지점)
curl -s localhost:8080/actuator/health
curl -s localhost:8080/actuator/prometheus | head
docker compose -f infrastructure/docker/compose.yaml logs --tail 5 gateway

# 5) 관측 포트 폐쇄 확인 — 3000/9090/3100/12345 응답 없어야 정상
```

### 실측 결과 (2026-08-21)

```text
> gradlew build                                    BUILD SUCCESSFUL ✅
  swtp-observability-starter 테스트 11건            failures=0 errors=0 ✅
    SwtpObservabilityEnvironmentPostProcessorTest 4  (prometheus 노출 단언 통과)
    SwtpLogbackConfigurationTest 5 / AutoConfigurationTest 2
  swtp-web-starter RequestIdMdcFilterTest 4         failures=0 ✅
  gateway AccessLog/RequestIdGlobalFilterTest 6     failures=0 ✅

> docker compose config --services                 관측 서비스 4종 부재 ✅
  config-server discovery-server ems-service gateway job-service
  kafka kafka-init master-service realtime-service telemetry-service timescaledb

> docker compose up -d --build                     exit 0, 전 컨테이너 healthy ✅

> curl localhost:8080/actuator/health              HTTP 200 {"status":"UP"} ✅
> curl localhost:8080/actuator/prometheus          HTTP 200 ✅
    jvm_memory_used_bytes{application="gateway",area="heap",...}
    http_server_requests_seconds_count{application="gateway",...}
    → 삭제한 대시보드가 쓰던 메트릭이 그대로 살아 있다

> docker compose logs gateway                      ECS JSON 1줄 ✅
    {"@timestamp":"...","log":{"level":"WARN"},"service":{"name":"gateway",
     "version":"1.0.0","node":{}},"ecs":{"version":"8.11"}}

> 관측 포트 3000 / 9090 / 3100 / 12345             전부 응답 없음 ✅
> docker volume ls (관측 볼륨)                      잔여 없음 — 수동 정리 불필요 ✅
```

**잔여 볼륨 수동 정리** (이번 환경에는 잔여가 없었다. 스택을 기동한 적 있는 정수장 서버에서는 필요하다):

```bash
docker volume ls --filter name=prometheus --filter name=loki --filter name=grafana --filter name=alloy
docker volume rm swtp-platform_prometheus-data swtp-platform_loki-data swtp-platform_grafana-data swtp-platform_alloy-data
```

**잔여 참조 확인:**

```bash
git grep -in -E "alloy|loki|grafana" -- ':!docs/'
```
→ "철거했다"고 알리는 `README.md`·`infrastructure/CLAUDE.md` 두 문장만 남아야 정상이다. `prometheus`는 `micrometer-registry-prometheus`·`exposure.include`·`/actuator/prometheus`로 **의도적으로 남아 있다**(결정 2).

> **주석도 grep 대상이다.** 이번에 `swtp-logback-ecs.xml`이 삭제된 `infrastructure/alloy/config.alloy`를 경로로 직접 참조하고 있는 것을 포함해 8곳의 낡은 주석이 발견됐다. 코드가 지운 심볼을 참조하면 빌드가 깨져 알려주지만 **주석이 지운 파일을 참조하면 아무도 알려주지 않는다.** 빌드 성공은 코드 정합의 증거일 뿐 문서 정합의 증거가 아니다.

## 다음 단계

2사이클 — ELK 도입. 이 문서의 "ELK 접점 명세"와 "미결 사항"이 입력이다.

✅ **완료 (2026-08-21) → [step-17-elk-observability.md](step-17-elk-observability.md)**

여기 남긴 접점 명세가 실제로 그대로 쓰였다. 특히 다음 세 가지가 2사이클에서 값을 냈다.

- **결정 2(`micrometer-registry-prometheus` 유지)** — Metricbeat `prometheus` 모듈이 앱 변경 0줄로 8개 타깃을 수집했다.
- **미결 1(정수장 식별 필드)** — 검증해 둔 `service.node.name` 해법이 설정 2줄로 끝났다.
- **함정 #2(config-server exposure 닫힘)** — `include`에 `prometheus` 한 단어를 더해 정정했고, 실측에서 config-server가 최다 수집 대상이 됐다.

미해결로 남은 것은 **SFR-016 구현 한계 4개 경계선의 고객사 합의**뿐이다.
