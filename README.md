# 스마트정수장 플랫폼 (swtp-platform)

정수장별 온프레미스 단일 서버에 배포하는 스마트정수장 시스템.
Gradle Multi-Module 기반 Mono Repository로 Java/Spring 서비스를 관리한다.

모놀리스 버전([swtp-monol](https://github.com/ohsse/swtp-monol))을 **정수장별 선택 배포가 가능한 MSA**로 다시 설계했다.

## 핵심 설계

| 주제 | 내용 |
|---|---|
| 서비스 분리 | 업무 앱 8종(auth·master·telemetry·realtime·ems·pms·autonomous·job) + 플랫폼 앱 3종(config-server·discovery·gateway) |
| 의존 방향 강제 | `apps → starters → libs` 단방향. 앱 간 `project()` 의존은 Convention Plugin(`build-logic`)이 **빌드 실패**로 막는다 |
| 공통 관심사 | `swtp-*` 자동구성 스타터 5종(web·security·persistence·kafka·observability) |
| 데이터 | PostgreSQL 단일 인스턴스 + 서비스별 스키마 소유, DDL은 Flyway 단독 소유, 시계열은 TimescaleDB hypertable |
| 이벤트 | Kafka(KRaft). 토픽은 auto-create를 끄고 스크립트로 명시 생성, Config Bus로 설정 전파 |
| 정수장별 배포 | compose profiles(`auth`/`autonomous`/`pms`/`ai`/`full`)로 Optional 서비스만 골라 띄운다 |
| 인증 | 게이트웨이 JWT(JWKS) 검증. 앞단 SSO가 있는 현장은 `none` 모드 |
| 관측 | ECS JSON 로그 → Filebeat, Actuator Prometheus → Metricbeat, ILM 보존 정책 |
| 테스트 | 통합 테스트는 H2 대신 운영과 같은 이미지의 **Testcontainers** |
| CI/CD | Jenkins: 통합 테스트 → 이미지 빌드 → 스모크 테스트 → `:prev` 태그 롤백 |

설계 결정과 함정 기록은 `docs/scaffold/steps/`(골격 21단계)와 각 모듈의 `docs/`에 남아 있다.

## 빠른 실행

```bash
./gradlew bootJar
cp infrastructure/docker/.env.example infrastructure/docker/.env
docker compose -f infrastructure/docker/compose.yaml up -d --build          # Core
docker compose -f infrastructure/docker/compose.yaml --profile full up -d   # Optional 포함
```

> 공개본의 DB 비밀번호(`swtp_local_dev`)는 로컬 데모 전용 값이다. 원본의 서버 주소와 계정 정보는 제거했다.

## 문서

- 아키텍처 기준안: [docs/architecture/스마트정수장_리빌드_아키텍처.md](docs/architecture/스마트정수장_리빌드_아키텍처.md)
- 스캐폴딩 계획: [scaffold/plan.md](docs/scaffold/plan.md)
- 단계별 실행 기록: [scaffold/steps/](docs/scaffold/steps/)

## 기술 스택

| 구성요소 | 버전 |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.0 |
| Spring Cloud | 2025.1.2 (Oakwood) |
| Gradle | 9.7.0 (wrapper) |
| PostgreSQL + TimescaleDB | 17 + 2.26 |
| Kafka | 4.x (KRaft) |
| Observability | Elasticsearch / Kibana / Filebeat / Metricbeat 9.4.5 (`elk` 프로파일) |

## 요구 환경

- JDK 21
- Docker Desktop (compose v2)

## 빌드

```bash
./gradlew build        # 전체 빌드
./gradlew projects     # 모듈 목록 확인
```

## 저장소 구조

```text
swtp-platform/
├─ build-logic/      # Gradle Convention Plugin (Groovy DSL)
├─ apps/             # 독립 실행 Spring Boot 애플리케이션 11종
├─ starters/         # swtp-* 공통 스타터
├─ libs/             # swtp-common
├─ ai/               # Python FastAPI (Gradle 모듈 아님)
├─ infrastructure/   # docker-compose, DB/Kafka 구성
├─ docs/             # 아키텍처 문서
└─ scaffold/         # 스캐폴딩 계획 및 단계별 실행 기록
```
### apps 하위 마이크로 서비스 구조

