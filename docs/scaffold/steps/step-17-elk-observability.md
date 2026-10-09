# Step 17 — ELK 관측 스택 도입

- 일자: 2026-08-21
- 상태: ✅ 완료 (수집·색인·조회 경로 전 구간 실동작 검증)
- 관련: [step-16-observability-stack-teardown.md](step-16-observability-stack-teardown.md), [step-13-log-format-switch.md](step-13-log-format-switch.md), 아키텍처 14장

## 발단

step-16에서 Grafana 스택을 철거하고 접점 명세만 남겼다. 이 문서는 그 접점 위에 ELK를 올린 2사이클의 기록이다. step-16의 "ELK 접점 명세"와 "미결 사항"이 그대로 입력이 됐다.

**1사이클의 판단이 여기서 값을 냈다.** 계측 파사드(actuator · `micrometer-registry-prometheus` · ECS 로그 · `X-Request-Id` MDC)를 남겨 뒀기 때문에 이번 사이클에서 **Java 앱 11종의 코드 변경이 0줄**이다. 바꾼 것은 인프라 설정과 `ai-service`(Gradle 모듈이 아니라 스타터를 못 쓰는 유일한 앱)뿐이다.

## 결정 (2026-08-21)

### 1. Beats 직결 — Logstash를 두지 않는다

Filebeat / Metricbeat → Elasticsearch 직결이다. SFR-016의 "ELK 기반" 문구를 문자 그대로 읽으면 Logstash가 필요해 보이지만, **우리 로그는 이미 ECS JSON이라 Logstash가 할 일이 사실상 통과(pass-through)다.** 단일 정수장 서버에 앱 11종 + Kafka + TimescaleDB가 이미 올라가 있는 상태에서 JVM 프로세스를 하나 더(힙 최소 1GB) 얹을 근거가 되지 않는다. 파싱이 필요해지면 Elasticsearch ingest pipeline이 같은 일을 프로세스 추가 없이 한다.

> 고객사가 Logstash 존재 자체를 검수 항목으로 삼는다면 이 결정을 뒤집어야 한다. 뒤집는 비용은 compose 서비스 1개 + 파이프라인 설정 1개이며, 앱과 Beats 설정은 그대로다.

### 2. Elasticsearch 보안을 끈다 (`xpack.security.enabled: false`)

폐쇄망 단일 서버 전제다. 켜면 부트스트랩 토큰 · 자체서명 TLS · Beats/Kibana 자격증명 배선이 전부 따라붙는데, 그 대가로 얻는 것이 정수장 내부망 안에서는 거의 없다. 대신 **9200을 루프백(`127.0.0.1`)에만 공개해** 노출면 자체를 없앴다.

> **중앙 집계로 전환하면 반드시 다시 켠다.** 그 순간 ES가 정수장 밖으로 노출된다.

### 3. `elk` 프로파일은 앱 프로파일(`full`)에 넣지 않는다

`full`은 문서 19장이 정의한 "Optional **앱** 묶음"이고 ELK는 앱이 아니라 관측 백엔드다. 개발자가 스모크 테스트로 `--profile full`을 칠 때 Elasticsearch까지 뜨면 단일 서버가 눌린다. 프로파일이 두 축이 되므로 함께 띄우려면 둘 다 지정한다.

### 4. 정수장 식별자는 ECS 표준 슬롯 `service.node.name`에 싣는다

커스텀 필드(`swtp.site` 등)를 신설하지 않는다. ECS가 이미 노드 식별 슬롯을 정의하고 있고 Boot 4가 그것을 `logging.structured.ecs.service.node-name`으로 노출한다. 표준 슬롯에 실으면 Beats 템플릿·Kibana 기본 매핑이 그대로 먹고, 나중에 Elastic Agent로 갈아타도 같은 자리에 남는다.

### 5. `ai-service`의 ECS 로그는 라이브러리 없이 직접 구현한다

`ecs-logging` 공식 라이브러리 대신 stdlib `logging.Formatter` 서브클래스를 쓴다. 목표가 "ECS 스펙 준수"가 아니라 **"Java 쪽 Boot 4가 실제로 뱉는 출력과 필드 단위로 일치"**이기 때문이다. 라이브러리가 뱉는 필드 집합은 Boot의 것과 완전히 같지 않아 어차피 대조가 필요하고, 그 대조를 코드로 박아 두는 편이 의존성을 하나 늘리는 것보다 낫다. `requirements.txt`는 2줄 그대로다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `infrastructure/docker/compose.yaml` | **`elk` 프로파일 서비스 5종 추가** (elasticsearch / kibana / elk-init / filebeat / metricbeat), named volume 3개, `SWTP_SITE_CODE` 전달(`x-app-env` · config-server · ai-service) |
| `infrastructure/filebeat/filebeat.yml` | **신규** — docker autodiscover + `swtp-` 필터 + ECS JSON 파싱 |
| `infrastructure/metricbeat/metricbeat.yml` | **신규** — docker 모듈 7종 + prometheus 모듈(Core 8타깃) |
| `infrastructure/elasticsearch/init-ilm.sh` | **신규** — 인덱스 보존 정책(ILM) 적용. `kafka/create-topics.sh`와 같은 자리 |
| `config-repo/application.yml` | `logging.structured.ecs.service.node-name: ${SWTP_SITE_CODE:}` 추가 |
| `apps/config-server/.../application.yml` | `exposure.include`에 `prometheus` 추가 (**step-16 함정 #2 정정**) |
| `ai/ai-service/logging_ecs.py` | **신규** — ECS JSON 포매터 + requestId ContextVar |
| `ai/ai-service/inference_status.py` | **신규** — SFR-016 세 신호 집계 (계약 골격) |
| `ai/ai-service/main.py` | requestId 미들웨어 · 접근 로그 · 하트비트 · `/health` 확장 |
| `ai/ai-service/Dockerfile` | 신규 모듈 2개 COPY |
| `infrastructure/docker/.env.example` | `SWTP_SITE_CODE` · `SWTP_ELASTIC_HOSTS` · `SWTP_ES_HEAP` · Kibana URL |
| `apps/` Java 11종 · `starters/` · `libs/` · `build-logic/` · `gradle/libs.versions.toml` | **변경 없음** |

**버전 고정: 9.4.5** (Elasticsearch / Kibana / Filebeat / Metricbeat 동일). 최신은 9.5.2지만 한 마이너 뒤의 마지막 패치를 택했다 — 정수장 온프레미스는 정착된 릴리스가 낫다. Elastic 스택은 구성요소 버전이 어긋나면 템플릿·대시보드 적재가 실패하므로 **넷을 항상 같은 값으로 올린다.**

---

## 구현 상세

### 로그 경로

```text
앱 stdout (ECS JSON 1줄)
   └─ Docker json-file 드라이버 → /var/lib/docker/containers/<id>/*.log
        └─ Filebeat (docker autodiscover, ^swtp- 필터)
             ├─ parsers: container   ← Docker 껍데기 제거
             └─ parsers: ndjson      ← ECS JSON을 필드로 펼침
                  └─ Elasticsearch (data stream filebeat-9.4.5)
                       └─ Kibana Discover (데이터 뷰 filebeat-*)
```

철거된 Alloy가 하던 네 가지를 Filebeat가 그대로 이어받는다 — docker discovery / `swtp-` 필터 / JSON 파싱 / 수집 위치 영속화(`swtp-filebeat-data` 볼륨).

**Loki 대비 실질 이점이 확인됐다.** Alloy는 카디널리티 폭발 때문에 `requestId`를 라벨로 올리지 못하고 본문에만 뒀다. Elasticsearch는 스트림/라벨 모델이 아니라 문서 색인이므로 이 제약이 없다 — `requestId`가 그냥 검색 가능한 필드다. 실제로 **한 요청의 게이트웨이 로그와 ai-service 로그가 `requestId` 하나로 묶이는 것을 확인했다**(아래 검증).

### 메트릭 경로

| 대상 | 수집 방식 |
|---|---|
| 컨테이너 상태·자원 | Metricbeat `docker` 모듈 (container / healthcheck / cpu / memory / network / diskio / info) |
| 앱 메트릭 | Metricbeat `prometheus` 모듈 → `/actuator/prometheus` (OpenMetrics) |
| `ai-service` | `docker` 모듈 + 자기 ECS 로그. actuator가 없어 prometheus 모듈 대상이 아니다 |

**Optional 서비스(auth / autonomous / pms)는 기본 주석 처리다.** 떠 있지 않은 대상을 정적 타깃에 두면 30초마다 접속 실패가 ERROR로 쌓여 하루 2,880건이 된다 — 진짜 장애 로그가 묻힌다. 배포하는 정수장만 `metricbeat.yml`의 해당 블록 주석을 푼다.

> 대안으로 `co.elastic.metrics/*` 라벨 기반 autodiscover를 검토했으나 **채택하지 않았다.** 앱 서비스 정의에 벤더 고유명이 박히기 때문이다. 1사이클에서 철거가 쉬웠던 이유가 "Alloy가 앱 정의에 흔적을 0개 남겼다"는 것이었는데, 라벨을 붙이면 정확히 그 반대를 재현하게 된다. 벤더 결합은 `infrastructure/metricbeat/` 디렉토리 안에 가둬 둔다 — 통째로 지우면 끝나도록.

### 정수장 식별자

```yaml
# config-repo/application.yml
logging.structured.ecs.service.node-name: ${SWTP_SITE_CODE:}
```

- 값이 있으면 → `"service":{"node":{"name":"GANGNEUNG-01"}}`
- 값이 비면 → Boot가 필드를 **아예 생략**한다(`"service":{"node":{}}`) — 빈 문자열이 색인되지 않는다
- 우리 설정을 안 읽는 컨테이너(kafka / timescaledb / elasticsearch / kibana)는 Filebeat의 `add_fields`가 `unknown`으로 채운다 → 전 문서에서 필드가 균일하다

**형식 규약**: 대문자 영숫자 + 하이픈. 공백·한글 금지 (Kibana 필터에 그대로 쓰인다). 예: `GANGNEUNG-01`. 실제 값은 정수장별 `.env`에서 정한다 — 고객사 기존 코드 체계가 있으면 그것을 쓴다.

### ai-service 로그 계약

Java 11종과 **같은 필드명**으로 맞춘 것이 전부다. 어긋나면 Kibana에서 상관관계가 AI 구간에서 끊긴다.

| 필드 | Java (Boot 4 StructuredLogEncoder) | Python (`logging_ecs.py`) |
|---|---|---|
| `@timestamp` | ISO-8601 나노초 | ISO-8601 **밀리초** (Beats 파서 레이아웃에 맞춤) |
| `log.level` | `WARN` (Logback) | `WARNING` → **`WARN`으로 매핑** |
| `log.logger` / `message` / `ecs.version` | 동일 | 동일 (`ecs.version` = 8.11) |
| `service.name` / `service.version` / `service.node.name` | 동일 | 동일 |
| `process.pid` / `process.thread.name` | 동일 | 동일 |
| `requestId` | MDC (`SwtpHeaders.REQUEST_ID_MDC_KEY`) | `ContextVar` |
| 접근 로그 | `swtp.gateway.access` — `http.method` `http.path` `http.status` `duration.ms` | `swtp.ai.access` — **같은 필드명** |
| 예외 | `error.type` / `error.message` / `error.stack_trace` | 동일 |

### SFR-016 세 신호 (`inference_status.py`)

| 신호 | 필드 | 노출 경로 |
|---|---|---|
| 프로세스 생존 | 하트비트 로그 존재 자체 | `swtp.ai` 로거, 기본 60초 주기 |
| 마지막 추론 시각 | `inference.last_at` | 하트비트 로그 + `/health` |
| 추론 에러율 | `inference.error_rate`(누적) / `inference.recent_error_rate`(최근 100건) | 동일 |

**여기는 계약 골격이다.** 추론 엔드포인트가 생기면 성공/실패마다 `status.record(ok)`를 부르면 되고 집계 방식과 노출 경로는 바꾸지 않는다.

ELK로 나가는 경로를 별도 수집기가 아니라 **자기 로그**로 잡은 이유: ai-service에는 actuator가 없어 Metricbeat가 긁을 OpenMetrics 엔드포인트가 없고, compose 프로파일로 선택 배포되는 서비스를 정적 타깃에 넣으면 미배포 정수장에서 실패 로그가 쌓인다. 자기가 로그로 뱉으면 Filebeat가 이미 그 컨테이너를 수집하고 있으므로 **추가 배선이 0**이다.

### 인덱스 보존 (ILM)

`elk-init` 컨테이너가 `filebeat` / `metricbeat` 정책을 적용한다 — `kafka-init`와 같은 자리다.

| 항목 | 값 | 환경변수 |
|---|---|---|
| 롤오버 | 1일 또는 주 샤드 5GB | `SWTP_LOG_MAX_SHARD_SIZE` |
| 삭제 | 30일 | `SWTP_LOG_RETENTION_DAYS` |

**Beats가 자동 생성하는 기본 정책에는 delete 단계가 없다** — rollover만 하고 지우지 않아 인덱스가 무한히 자란다. 정수장 온프레미스 단일 서버에서는 이것이 디스크를 채우고, 디스크가 차면 Elasticsearch가 읽기 전용으로 잠기면서 수집이 멈춘다. 관측 스택이 스스로를 관측하지 못하게 되는 상태다.

Beats는 정책이 이미 있으면 덮어쓰지 않으므로 `elk-init`가 **Beats보다 먼저** 돌아야 한다 — `depends_on: service_completed_successfully`가 순서를 보장한다.

### 중앙 집계 전환 (사이트별 상이 구성)

step-16에서 "추후 전환 비용이 큰가"에 답으로 설계한 부분이다. **코드 변경 0, 두 줄이다.**

| 구성 | `--profile elk` | `SWTP_ELASTIC_HOSTS` |
|---|---|---|
| 정수장 서버 동거 (기본) | 켠다 | `http://elasticsearch:9200` |
| 중앙 집계 | **켜지 않는다** | `http://<중앙주소>:9200` |

Beats는 여전히 그 정수장 서버에서 돌고 출력만 밖으로 나간다. `service.node.name`이 이미 전 문서에 실려 있으므로 중앙에서 정수장을 구분할 수 있다 — 재색인이 필요 없다.

---

## 함정 기록

1. **Beats 9.x에서 `-environment container` 플래그가 제거됐다.**
   8.x 시절 컨테이너 실행의 관용구였고 이미지 기본 CMD이기도 했다. `command:`를 덮어쓰면서 관례대로 다시 적었더니 `unknown command "container"`로 즉시 종료했다.
   **`docker compose up`은 성공으로 끝나고 컨테이너만 조용히 사라진다** — `ps` 목록을 세어 보지 않으면 놓친다. 실제로 이번에 다른 13개가 healthy로 뜬 것을 보고 정상이라 판단할 뻔했다.

2. **`add_fields`가 앱이 이미 실은 필드를 다중값으로 만든다 — 가드가 필수다.**
   Filebeat `add_fields: {target: service.node}`는 문서에 **평면 점표기 키**(`"service.node"`)로 들어가는데, Elasticsearch는 색인할 때 점을 경로로 펼친다. 앱이 실은 중첩 `service.node.name`과 같은 필드로 합쳐지면서 한 문서에 값이 두 개 들어간다.
   실측: 가드 없이 돌렸더니 gateway 문서 **138건이 `GANGNEUNG-01`과 `unknown` 양쪽에 매칭**됐다.
   → `when.not.has_fields: ["service.node.name"]`로 앱이 채운 문서는 건드리지 않는다. step-16 함정 #4(ECS JSON은 중첩 객체다)가 수집기 쪽에서 되풀이된 형태다.

3. **`logging.to_files: false`만 두면 Beats가 어디로도 로그를 내지 않는다.**
   파일 출력만 끄고 stderr를 켜지 않아 `docker compose logs filebeat`가 완전히 비었다. **정상 동작 중인 것과 죽어 있는 것이 구분되지 않는다** — 수집기 자체의 침묵은 위험한 상태다. 실제로 이번에 "로그가 없다"를 보고 고장을 의심했으나 ES에는 5,039건이 들어와 있었다.
   → `logging.to_stderr: true`를 함께 적는다.

4. **uvicorn의 접근 로그는 ASGI 미들웨어 바깥에서 찍힌다.**
   `request_id_var`(ContextVar)를 미들웨어에서 세워도 `uvicorn.access` 로거는 프로토콜 계층에서 동작해 그 범위 밖이다 — 서블릿에서 필터 바깥의 MDC가 비어 있는 것과 같은 구조다. 포맷은 ECS로 잘 나가는데 **`requestId`만 조용히 빠져** 계약이 절반만 성립한다.
   → uvicorn 접근 로그를 끄고 미들웨어가 직접 남긴다. 게이트웨이와 필드명을 맞추는 부수 효과도 있다.

5. **Beats 기본 ILM에 delete 단계가 없다.** 위 "인덱스 보존" 참조.

6. **Kibana 데이터 뷰는 `setup.dashboards`가 만든다.**
   Metricbeat만 켜 두고 Filebeat에는 넣지 않았더니 `metricbeat-*` 데이터 뷰만 생겼다. 색인은 정상인데 **Discover에서 로그를 열 수단이 없는 상태**가 된다 — ES에 있는데 안 보인다.

## 알려진 한계

1. **Elasticsearch 인증이 없다.** 폐쇄망 전제의 의도된 선택이다(결정 2). 9200은 루프백에만 공개하지만 **Kibana 5601은 정수장 내부망에 열려 있고 인증이 없다.** 외부망 노출은 방화벽에서 차단해야 한다. 중앙 집계 전환 시 반드시 보안을 켠다.
2. **Metricbeat 기본 대시보드 112개가 함께 적재된다.** GKE·Azure·AWS 등 우리와 무관한 것이 대부분이다. Elastic이 모듈 전체 대시보드를 한 번에 올리는 구조라 선택 적재가 안 된다. 실제로 쓰는 것은 `[Metricbeat Docker] Overview ECS` 하나다.
3. **Optional 서비스 메트릭은 수동 주석 해제가 필요하다.** 정수장별로 `metricbeat.yml`이 달라지는 첫 사례다. "설정 파일은 전 정수장 동일" 원칙에서 벗어나 있다.
4. **ELK 스택 자신의 로그도 수집된다** (elasticsearch / kibana / filebeat / metricbeat 컨테이너가 `swtp-` 접두사를 갖는다). 진단에는 유용하나 색인 부피의 상당 부분을 차지한다. Filebeat가 자기 로그를 수집하는 되먹임은 발행 건당 로그를 남기지 않으므로 폭주하지 않지만, 부피가 문제가 되면 `^swtp-` 필터를 좁힌다.
5. **디스크 총량 상한은 없다.** ILM은 인덱스별 보존이지 디스크 총량 제한이 아니다. 정수장 서버의 실제 로그량이 나온 뒤 `SWTP_LOG_RETENTION_DAYS`를 재산정해야 한다.
6. **Elasticsearch 힙 1GB는 출발점이다.** 단일 서버에 앱 11종 + Kafka + TimescaleDB가 이미 있으므로 실부하 측정 후 재산정한다.

## 검증

```bash
./gradlew build                       # 앱 코드 무변경이므로 무수정 통과해야 정상
export SWTP_LOG_FORMAT=ecs SWTP_SITE_CODE=GANGNEUNG-01
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml --profile elk --profile ai up -d --build
```

### 실측 결과 (2026-08-21)

```text
> gradlew build                        BUILD SUCCESSFUL  (앱·스타터 무수정 통과)

> docker compose ps                    컨테이너 15종 전부 기동
    앱 11 + timescaledb + kafka + [elk] elasticsearch kibana filebeat metricbeat

> elk-init                             ILM 적용 완료, exit 0
    filebeat   phases=[hot, delete]  rollover=1d/5gb  delete.min_age=30d
    metricbeat phases=[hot, delete]  rollover=1d/5gb  delete.min_age=30d

── 로그 ──────────────────────────────────────────────────
> filebeat 색인                        7,120건 / 15.2mb
> 컨테이너별 수집 (15종 전부)           kafka 1433 · elasticsearch 584 · config-server 540
                                       · job 503 · kibana 374 · telemetry 249 · discovery 201
                                       · realtime 200 · master 197 · ems 180 · gateway 153
                                       · ai-service 101 · filebeat 57 · metricbeat 14 · timescaledb 14
> service.node.name 값 분포            GANGNEUNG-01 4,800건 (단일 값)
> 다중값 문서 수                        0건   (가드 적용 전에는 gateway 138건이 두 값에 매칭)

── 서비스 간 상관관계 (이번 사이클의 핵심) ─────────────────
> curl -H "X-Request-Id: corr-1787287789" → ai-service(8000) + gateway(8080)
> ES에서 requestId로 조회               2건, Java·Python 문서가 한 ID로 묶임

  service.name      : ai-service          |  gateway
  log.logger        : swtp.ai.access      |  swtp.gateway.access
  @timestamp        : 04:49:50.209Z       |  04:49:50.541Z   ← 앱 발생 시각 보존
  http              : GET /health 200     |  GET /master-service/actuator/health 200
  duration.ms       : 0                   |  209
  gateway           : —                   |  route=master-service target=http://<internal-host>:8081/...
  service.node.name : GANGNEUNG-01        |  GANGNEUNG-01

  → 두 문서의 http.* / duration.ms / requestId / service.node.name 구조가 완전히 동일하다.
  → @timestamp는 수집 시각이 아니라 앱 발생 시각이다 (Java 나노초 → 밀리초 절삭).

── 메트릭 ────────────────────────────────────────────────
> metricbeat 색인                      13,211건 / 8mb
> prometheus 모듈 타깃                  8종 전부 수집
    config-server 1620 · telemetry 900 · job 828 · realtime 816
    · master 737 · ems 671 · gateway 644 · discovery 624
    ※ config-server가 최다 = step-16 함정 #2 정정이 실제로 먹혔다는 증거
      (이전에는 exposure.include가 닫혀 있어 타깃에 있으면서 수집된 적이 없었다)
> docker 모듈 metricset                container 180 · cpu 180 · diskio 180 · memory 180
                                       · network 180 · healthcheck 155 · info 12
> 철거한 Grafana 대시보드 메트릭 생존    jvm_memory_used_bytes 768
                                       process_cpu_usage 96
                                       http_server_requests_seconds_count 266
> prometheus.labels.application        8종 전부 (Grafana 템플릿 변수가 쓰던 축 그대로)

── 조회 ──────────────────────────────────────────────────
> Kibana 데이터 뷰                      filebeat-* / metricbeat-*
> Kibana 대시보드                       112개 적재, [Metricbeat Docker] Overview ECS 포함

── 회귀 (기본 경로) ───────────────────────────────────────
> plain + 빈 SWTP_SITE_CODE            정상 기동, 평문 로그
> ecs  + 빈 SWTP_SITE_CODE             "service":{...,"node":{}}  ← 필드 생략, 빈 문자열 없음
> ecs  + SWTP_SITE_CODE=GANGNEUNG-01   "node":{"name":"GANGNEUNG-01"}
```

### 운영 확인 지점

```bash
docker compose -f infrastructure/docker/compose.yaml --profile elk ps   # elk-init는 Exited(0)가 정상
curl -s localhost:9200/_cat/indices?v                                   # 루프백만 열려 있다
docker compose -f infrastructure/docker/compose.yaml logs filebeat      # 수집기 침묵은 이상 신호다
# Kibana: http://<정수장서버>:5601  → Discover(filebeat-*) / Dashboard([Metricbeat Docker] Overview ECS)
```

## 다음 단계

- **SFR-016 구현 한계 4개 경계선 고객사 합의** (step-16 표). 특히 "ELK 기반 상태 정보 제공"을 Kibana 대시보드로 볼지 플랫폼 화면 임베드로 볼지가 최대 스코프 리스크다.
- **정수장 코드 값 체계 확정** — 형식은 규정했고 값만 남았다. 고객사 기존 체계가 있으면 그것을 쓴다.
- **실부하 기준 리소스 재산정** — ES 힙 1GB, 보존 30일, 샤드 5GB는 전부 출발점이다.
- **추론 엔드포인트 구현 시 `status.record(ok)` 연결** — 계약 골격은 준비돼 있다.
- **중앙 집계 전환 시 보안 활성화** — 결정 2의 전제가 그때 깨진다.
