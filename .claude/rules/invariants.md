# 불변식 — 루트 CLAUDE.md의 7개를 작업 전에 대조한다

루트 `CLAUDE.md`「불변식」절이 원본이다. 이 파일은 `/step`이 게이트에서 충돌 검사를 할 때 읽는 대조표이고, 원본이 바뀌면 이 표를 따라 고친다(`/claude-md-sync` 영역). 충돌을 어떻게 보고하고 뒤집는 요청을 어떻게 다루는지는 `/step` 3절이 정한다 — 여기엔 **무엇이 불변식인가**만 둔다.

| # | 불변식 | 자주 걸리는 요청 |
|---|---|---|
| 1 | 앱 모듈 간 `project()` 의존 금지 — 공유는 libs/starters 또는 HTTP·Kafka | "A 서비스의 DTO를 B에서 import" |
| 2 | 버전은 `gradle/libs.versions.toml`에서만 — build.gradle에 버전 문자열 금지 | 라이브러리 추가 시 좌표에 버전을 직접 씀 |
| 3 | DDL은 Flyway 단독 소유(`ddl-auto: none`), 스키마 생성은 `infrastructure/postgresql/init/01-schemas.sql` | `ddl-auto: update`로 "일단 띄우기" |
| 4 | Redis 미사용 — 공유 영속 상태는 PostgreSQL | 캐시·세션·락에 Redis 제안 |
| 5 | 통합 테스트에 H2 금지 — 운영과 같은 이미지의 Testcontainers | "테스트 빨리 돌리려고 H2" |
| 6 | Spring Boot 4 이름 체계 — `-webmvc`·`-flyway`·`-batch-jdbc`·`-resttestclient` | Boot 3 시절 `spring-boot-starter-web` |
| 7 | 주석·문서·커밋 메시지는 한국어 | 영문 커밋 메시지, 영문 Javadoc |

불변식을 건드리는 작업의 전문가 소환 인원은 `perspectives.md` 판정표가 정한다.

> 이 리포의 실패 예: "게이트웨이를 정적 URI 라우팅으로 바꾸자"는 그럴듯하지만 `docs/scaffold/plan.md`에서 이미 철회된 안이다. 코드부터 읽으면 "가능합니다"라고 답하게 된다.
