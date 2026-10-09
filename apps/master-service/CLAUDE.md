# master-service — 기준정보 소유

정수장·공정·설비·태그·계측기의 **생성/수정 책임을 독점**한다(아키텍처 5.1). 다른 서비스는 조회만 하고 마스터 데이터를 직접 쓰지 않는다 — 논리적 소유권이 DB 권한이 아니라 규약으로만 지켜지므로, 다른 서비스에서 master 스키마에 INSERT/UPDATE하는 코드가 보이면 그 자체가 결함이다.

## 스키마 2개를 소유한다

`master`는 자기 도메인이고, `operation`은 **제어 이력 공용 write 스키마인데 DDL 소유만 여기가 갖는다**(아키텍처 11.3의 명시적 예외). autonomous-service 등이 여기에 기록하지만 테이블을 만들고 바꾸는 것은 master-service의 Flyway다. `spring.flyway.schemas: master,operation`이 그 선언이다.

JPA(`data-jpa`)를 쓰므로 스키마 지정은 `hibernate.default_schema`다 — JdbcClient를 쓰는 telemetry·job과 방식이 다르다.

## 도메인 4종

`tag` / `prcs`(공정) / `eqp`(설비) / `fclt`(시설). **패키지 배치는 `apps/CLAUDE.md`의 「패키지 규약」이 유일한 출처이고, 이 앱이 그 A형의 기준형이다** — 여기 다시 적지 않는다.

`support`에 `ColLength`(컬럼 길이 상수)와 `UseYn`이 있다. 컬럼 길이를 엔티티마다 리터럴로 적지 않는다. `ColLength`는 **도메인별로 쪼개지 않는다** — `tag/domain/EqpTag`가 `EQP_ID` 길이를 쓰므로 쪼개면 도메인 간 참조 금지 규칙에 걸려 순환이 된다. 값의 SSOT는 자바 상수가 아니라 `V2__master_domain.sql`이다.

**엔티티에 `@ManyToOne` 등 연관관계를 두지 않는다.** 도메인 간 연결은 ID(`String`) 스칼라로만 한다 — DDL에 FK가 없는 것과 짝이고, `service`가 엔티티를 트랜잭션 밖으로 내보내도 지연로딩 사고가 없는 근거이기도 하다.

## 함정

**`spring.profiles.active: dev`가 `application.yml`에 박혀 있다 — 11개 앱 중 유일하다.** 로컬 `bootRun`에서는 config-server가 `config-repo/application-dev.yml`을 함께 내려주고, 그 파일이 `swtp.auth.mode: none`이라 **인증이 꺼진 채로 뜬다.** 컨테이너에서는 compose의 `SPRING_PROFILES_ACTIVE=docker`가 덮으므로 재현되지 않는다 — 즉 로컬에서만 통과하고 배포하면 401이 나는 조합이 성립한다.

**`failOnNoDiscoveredTests = false` 블록은 제거됐다.** sample 슬라이스 폐기로 실행 가능한 테스트가 0개였던 동안만 필요했던 가드이고, `OperationMigrationIntegrationTest`가 들어오면서 그 전제가 사라졌다(`docs/02-운전모드-변경이력-DDL.md`). 다시 넣지 않는다 — 남겨 두면 진짜로 테스트가 사라진 순간을 빌드가 알려주지 못한다.

**`swtp-kafka-starter`를 의존하지만 아직 아무 이벤트도 발행하지 않는다.** `master.changed` 토픽은 `infrastructure/kafka/create-topics.sh`에 이미 생성돼 있다 — 소비자(telemetry·AI)가 먼저 붙어 있을 수 있으므로 발행을 시작할 때 `EventEnvelope` 규약(`starters/CLAUDE.md`)을 따른다.
