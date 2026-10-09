# swtp-platform

정수장별 온프레미스 단일 서버에 배포하는 스마트정수장 플랫폼.
Gradle 멀티모듈 모노레포 — Java 21 / Spring Boot 4.1.0 / Spring Cloud 2025.1.2.

## 모듈 트리

| 디렉토리 | 역할 | 규약 |
|---|---|---|
| `build-logic/` | Gradle Convention Plugin | `build-logic/CLAUDE.md` |
| `libs/swtp-common` | 의존 최소 공용 계약 (ErrorCode·헤더·JWT 클레임·SPI) | — |
| `starters/` | `swtp-*` 자동구성 스타터 5종 | `starters/CLAUDE.md` |
| `apps/` | 독립 실행 Spring Boot 앱 11종 | `apps/CLAUDE.md` |
| `config-repo/` | Config Server(native)가 서빙하는 설정 | `config-repo/CLAUDE.md` |
| `infrastructure/` | docker compose · DB/Kafka/관측 구성 | `infrastructure/CLAUDE.md` |
| `ai/ai-service` | Python FastAPI (Gradle 모듈 아님) | — |

의존 방향은 `apps → starters → libs` 단방향이다.

## 불변식

1. **앱 모듈 간 `project()` 의존 금지.** 공유가 필요하면 libs/starters로 올리거나 HTTP·Kafka로 통신한다. `swtp.spring-boot-app` 규약이 빌드를 실패시킨다.
2. **버전은 `gradle/libs.versions.toml`에서만 변경한다.** build.gradle에 버전 문자열을 쓰지 않는다 — BOM `platform()`이 정렬한다.
3. **DDL은 Flyway 단독 소유**(`ddl-auto: none`). 서비스는 자기 스키마만 소유하고, 스키마 자체의 생성은 `infrastructure/postgresql/init/01-schemas.sql`이 한다.
4. **Redis 미사용.** 공유 영속 상태는 PostgreSQL에 둔다.
5. **통합 테스트에 H2 금지.** 운영과 같은 이미지의 Testcontainers를 쓴다.
6. **Spring Boot 4 이름 체계**를 쓴다 — `spring-boot-starter-webmvc`(구 `-web`), `-flyway`, `-batch-jdbc`, `-resttestclient`.
7. 주석·문서·커밋 메시지는 한국어로 쓴다.

## 명령

```bash
./gradlew build                 # 전체 빌드 (통합 테스트는 Docker Desktop 필요)
./gradlew :apps:<name>:bootRun
./gradlew bootJar               # 컨테이너 빌드 사전 조건
docker compose -f infrastructure/docker/compose.yaml up -d --build
```

## 문서

- 아키텍처 기준: `docs/architecture/`
- **골격 단계 결정 이력 (동결)**: `docs/scaffold/` — plan.md + step-00~20. 계속 읽되 **새 문서를 추가하지 않는다**
- **구현 이력** ("왜 이렇게 했는가"의 1차 출처): 각 모듈의 `docs/` — `apps/<service>/docs/`, `starters/docs/`, `infrastructure/docs/` 등
- 검토 후 미착수: `docs/미착수.md`
- CLAUDE.md 정합성 점검 이력: `docs/sync/`

## 갱신 규칙

작업 착수 시 해당 모듈 `docs/`에 문서를 열고(`🔍 논의`) 완료(`✅`)까지 같은 파일에서 굴린다.
여러 모듈에 걸치면 무게중심 디렉토리에 한 벌만 두고 나머지에서 링크한다.
CLAUDE.md는 **위 불변식이 바뀐 경우에만** 수정한다 — 설명과 이력은 문서 쪽에 둔다.
