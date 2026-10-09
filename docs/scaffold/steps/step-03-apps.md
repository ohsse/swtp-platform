# Step 03 — apps 11개 모듈 골격 + ai-service

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 3

## 목표

전체 앱 모듈 트리를 실행 가능한 골격으로 만들고, **Cloud 2025.1.2 ↔ Boot 4.1.0 호환성을 실기동으로 첫 검증**한다.

## 실행 내용

### 1. 생성 모듈 (settings.gradle에 11개 include, 포트 고정표 적용)

| 모듈 | 포트 | convention | 비고 |
|---|---|---|---|
| apps/config-server | 8888 | spring-cloud-app | `@EnableConfigServer`, **native 백엔드** → 루트 `config-repo/` 서빙 (`optional:` 접두로 미존재 시에도 기동) |
| apps/discovery-server | 8761 | spring-cloud-app | `@EnableEurekaServer`, 자기 등록/조회 off |
| apps/gateway | 8080 | spring-cloud-app | `spring-cloud-starter-gateway-server-webflux`, **정적 URI 라우팅 8건** (`spring.cloud.gateway.server.webflux.routes` — Cloud 2025 신규 네임스페이스) |
| apps/{master,telemetry,realtime,job,auth,autonomous,ems,pms}-service | 8081~8088 | spring-boot-app | `web-starter` + `spring-boot-starter-webmvc`(Boot 4 모듈화 스타터명) |

각 앱 공통: `@SpringBootApplication` + `application.yml`(포트/이름) + `@SpringBootTest` contextLoads 스모크 테스트.
비즈니스 앱 8종은 `web-starter` 의존으로 springdoc(swagger-ui)까지 함께 획득.

### 2. ai/ai-service (FastAPI — Gradle 모듈 아님, 아키텍처 문서 6장)

- `main.py`: `/health`(actuator 형태 `{"status":"UP"}`) — **Swagger는 FastAPI 내장**(`/docs`, `/openapi.json`)이라 별도 의존 불필요
- `requirements.txt`: fastapi 0.141.1 / uvicorn 0.52.1 (2026-08 웹 검증, 명시 고정)
- `Dockerfile`: python:3.13-slim, 포트 8000 (Phase 5 compose 연결)
- **Eureka 등록**: `py-eureka-client`로 가능하나, 플랫폼 원칙(정적 라우팅 + 등록 optional)에 따라 미적용. 다중 서버 확장 시 도입 검토

### 3. 앱 간 의존 금지 가드 (아키텍처 원칙의 빌드 강제)

`swtp.spring-boot-app.gradle`에 afterEvaluate 검사 추가 — compile/runtimeClasspath의
ProjectDependency 중 `:apps:` 경로 발견 시 GradleException으로 **설정 단계에서 즉시 실패**.

부정 테스트 수행: master-service에 `project(':apps:telemetry-service')` 임시 추가 →
`앱 모듈 간 직접 의존은 금지된다: :apps:master-service -> [:apps:telemetry-service]` 메시지로 실패 확인 후 원복. ✅

## 검증 결과

```text
> gradlew build
BUILD SUCCESSFUL — 108 태스크, 11개 앱 컴파일 + contextLoads 테스트 전부 통과 ✅

> java -jar (config-server / gateway / master-service 동시 기동)
GET :8888/actuator/health → 200 {"groups":["liveness","readiness"],"status":"UP"}
GET :8080/actuator/health → 200 (동일)
GET :8081/actuator/health → 200 (동일)
→ Cloud 2025.1.2 ↔ Boot 4.1.0 첫 실기동 검증 통과 (리스크 1번 해소) ✅
```

알려진 무해 로그: discovery-server 테스트 셧다운 시 `EurekaJersey3ClientImpl: Cannot clean connections`
(커넥션 클리너와 클라이언트 종료 간 경합) — 테스트는 통과, 기능 영향 없음.

## 주요 결정

- **비즈니스 앱 8종 모두 web-starter 의존**: 전부 REST API를 노출할 예정이므로 일괄 적용 (springdoc 포함)
- **config-server 클라이언트 연동은 Phase 5로 유보**: 이번 골격은 각 앱 standalone 기동. 컨테이너 통합 시 `spring.config.import` 연결
- **gateway 라우팅 URI는 localhost 고정**: 컨테이너 환경 URI는 config-repo 재정의로 해결 예정

## 다음 단계

Phase 4 — 인프라 docker-compose: PG+TimescaleDB(스키마 10개 init), Kafka KRaft(토픽 8종 init 컨테이너), Prometheus/Loki/Alloy/Grafana
