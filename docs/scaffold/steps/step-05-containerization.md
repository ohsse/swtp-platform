# Step 05 — 앱 컨테이너화 + 전체 스택 통합

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 5

> **후속 변경 (2026-08-21)** — 여기 기록된 Grafana 관측 스택(Alloy/Loki/Prometheus/Grafana)은
> 철거됐다. 고객사 요구사항 SFR-016이 ELK를 강제한다. 앱 계측(actuator·ECS 로그·requestId MDC)은
> 그대로 유지되며, ELK가 붙을 접점은 별도 문서에 명세돼 있다.
> → [step-16](step-16-observability-stack-teardown.md)

## 목표

Java 앱 11종 + ai-service를 컨테이너로 compose 스택에 합류시키고,
config-server 중심의 환경별 설정 체계(docker 프로파일)와 관측성 파이프라인을 전체 스택 기준으로 완성한다.

## 실행 내용

### 1. 공통 Dockerfile (`infrastructure/docker/app.Dockerfile`)

- 빌드 컨텍스트 = 리포지토리 루트, `--build-arg APP=<모듈명>` 하나로 11개 앱 전부 커버
- 호스트에서 `gradlew bootJar` 선행 → 이미지 안에서는 Gradle을 돌리지 않는다 (빌드 시간 절약)
- Boot 4의 `-Djarmode=tools ... extract --layers`로 레이어 추출 → 의존성 레이어 캐시 재사용
- 루트 `.dockerignore`(`*` + `!apps/*/build/libs/*.jar`)로 데몬 전송 컨텍스트 최소화
- convention plugin에서 앱 모듈 plain jar 비활성(`jar.enabled=false`) → `COPY *.jar` 와일드카드 안전
- 런타임 이미지: eclipse-temurin:21-jre + curl(healthcheck용)

### 2. config-server 연동 (Phase 3 유보분 해소)

- config-server 제외 **전 앱 10종**에 `spring-cloud-starter-config` 추가
  - 비즈니스 앱 8종은 Cloud BOM 필요 → convention plugin을 `swtp.spring-boot-app` → `swtp.spring-cloud-app`으로 전환
- 각 앱 yml: `spring.config.import: "optional:configserver:${CONFIG_SERVER_URI:http://localhost:8888}"`
  - **optional** → config-server 미가동 시에도 단독 기동/테스트 가능 (로컬 개발 흐름 유지)
- 컨테이너 환경은 `SPRING_PROFILES_ACTIVE=docker` + `CONFIG_SERVER_URI=http://config-server:8888` 주입
- config-repo 신규:
  - `application-docker.yml` — docker 프로파일 공통 (Phase 6부터 Kafka/DB 접속 정보 배치)
  - `gateway-docker.yml` — **라우팅 URI를 컨테이너 서비스명으로 재정의** (localhost → master-service:8081 등 8건)
- config-server 컨테이너는 `config-repo/`를 read-only 마운트 + `SEARCHLOCATIONS=file:/config-repo`

### 3. compose 앱 합류 (`infrastructure/docker/compose.yaml`)

| 구분 | 서비스 | profile |
|---|---|---|
| Core (기본 기동) | config-server, gateway, master, telemetry, realtime, job | - |
| Optional (문서 19장 선택 배포) | auth / autonomous / ems / pms / ai | 각자 이름 + `full` |
| 유보 (Eureka 원안) | discovery-server | `discovery` + `full` |

> **이후 변경**: discovery-server와 ems-service는 Core로 승격되어 profile이 제거되었고,
> Optional은 `auth / autonomous / pms / ai` 4종으로 줄었다. Eureka 활성화로 라우팅이 `lb://`가 되면서
> `gateway-docker.yml`(컨테이너용 URI 복제본)도 삭제되었다. 아키텍처 3.1.2 · 4.3 · 19장 참조.

- YAML 앵커(`x-app-build`, `x-app-env`, `x-depends-config`)로 중복 제거
- healthcheck = `curl /actuator/health`, 기동 체인: 앱 → config-server(healthy), gateway → Core 4종(healthy)
- Optional은 profile 이름 개별 부여 → `--profile ems --profile ai` 조합 기동 가능, `full`은 전체 묶음

### 4. 관측성 전체 스택 전환

- Prometheus 타깃을 **컨테이너 서비스명으로 전환**, Core(`swtp-apps`)/Optional(`swtp-apps-optional`) job 분리
  — Optional 미기동 시 up=0 표시가 정상임을 job 단위로 구분
- Grafana datasource에 **uid 고정**(prometheus/loki) + JVM 대시보드 프로비저닝
  (`swtp/jvm-overview.json`: Heap/CPU/HTTP 처리율/컨테이너 로그, `$application` 변수로 서비스 선택)

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| Grafana 재시작 실패 (`data source not found`) | 기존 볼륨에 uid 없이 프로비저닝된 datasource가 있는 상태에서 uid를 새로 부여 → uid 기준 갱신 대상 미존재 | 로컬 Grafana 볼륨 초기화(`grafana-data` 삭제) 후 재기동 — 자동 생성물만 있던 상태라 손실 없음 |
| Prometheus에 `host.docker.internal` 타깃 잔존 표시 | 설정 전환 직후 restart로 staleness 마커 없이 남은 이전 시계열 | 수 분 내 자연 소멸 (신규 설정에는 미포함) |
| PowerShell→curl.exe로 LogQL 전달 시 따옴표 유실 | 네이티브 인자 전달 규칙 | `[uri]::EscapeDataString` 선인코딩 후 호출 |

## 검증 결과

```text
> gradlew build                                  BUILD SUCCESSFUL ✅ (plain jar SKIPPED 확인)
> docker compose --profile full build            12 이미지 빌드 ✅ (exit 0)
> docker compose --profile full up -d            19 컨테이너 전부 (healthy) ✅

[gateway 경유 라우팅] GET :8080/actuator/health → 200 ✅
GET :8080/api/master/ping → 404 응답이 master-service에서 발생
  (master 메트릭 http_server_requests{status="404",uri="/**"} = 1 → 컨테이너명 라우팅 도달 증거) ✅
config-server GET /gateway/docker → gateway-docker.yml propertySources 서빙 ✅

[관측성] Prometheus: 컨테이너명 타깃 11종 전부 up=1 ✅
Grafana: datasource 2종(uid 고정) + "SWTP JVM Overview" 대시보드 프로비저닝 ✅
  jvm_memory_used_bytes — 컨테이너 앱 11종 전부 수집 (heap 38~59MB) ✅
Loki: {container="swtp-gateway"} → Started GatewayApplication 로그 수신 ✅
ai-service GET :8000/health → {"status":"UP"} ✅
```

## 결정 사항

- 이미지 빌드는 **호스트 bootJar + 레이어 추출** 방식 (멀티스테이지 Gradle 빌드는 CI 도입 시 재검토)
- 비즈니스 앱 규약이 `swtp.spring-cloud-app`으로 통일됨 — config client가 플랫폼 표준이 되었기 때문.
  `swtp.spring-boot-app`은 Cloud 비의존 앱을 위한 베이스 규약으로 유지
- 컨테이너 리소스 제한(mem_limit 등)은 미설정 — 로컬 개발 단계, 운영 배포 구성 시 재검토

## 다음 단계

Phase 6 — 수직 슬라이스 1: Master (web-starter 실구현 + Flyway V1 + CRUD + master.changed 발행)
