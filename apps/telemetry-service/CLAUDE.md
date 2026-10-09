# telemetry-service — 시계열 데이터

`telemetry.raw`를 소비해 TimescaleDB hypertable에 적재하고, 현재값·기간·집계 조회를 제공한다(아키텍처 5.2·12장).

## JPA를 쓰지 않는다

`swtp-persistence-starter`만 쓰고 `spring-boot-starter-data-jpa`를 의존하지 않는다. 시계열은 **append-only라 영속성 컨텍스트가 무용**하고, 더티체킹·1차 캐시가 대량 적재에서 손해다(Phase 7 결정). `JdbcClient`로 직접 쓴다.

부수 효과가 하나 있다 — data-jpa가 없으면 **JPA Auditing이 로드되지 않는다.** 그래서 `BaseEntity`의 감사 컬럼 자동 주입도 여기서는 동작하지 않는다. 감사 정보가 필요하면 SQL에서 직접 넣는다.

## 스키마는 `search_path`로 정한다

`hikari.connection-init-sql: SET search_path TO telemetry, public, ext`. **SQL에 스키마명을 하드코딩하지 않는다.**

`hikari.schema`(=`setSchema()`)를 쓰면 안 된다 — 경로를 통째로 대체해 `ext` 확장 스키마가 조용히 사라지고, 그러면 TimescaleDB 함수 호출이 런타임에 깨진다.

## hypertable 마이그레이션 규약

```sql
-- PK는 파티션 컬럼을 반드시 포함해야 한다 (hypertable 제약)
PRIMARY KEY (tag_id, measured_at)
-- 함수는 public으로 정규화해 호출한다 — search_path에 있어도 마이그레이션 시점에는 보이지 않는다
SELECT public.create_hypertable('...', public.by_range('measured_at', INTERVAL '7 days'));
```

`public.` 접두사를 빠뜨리면 인프라 init이 설치한 timescaledb 함수를 찾지 못해 Flyway가 실패한다.

## 소비

`@KafkaListener(topics = "telemetry.raw")`. 컨슈머 그룹은 kafka-starter가 `${spring.application.name}`으로 넣으므로 선언하지 않는다 — **realtime-service가 같은 토픽을 다른 그룹으로 함께 소비**하며, 둘 다 전량을 받는 팬아웃 구조다. 그룹 id를 손으로 적어 겹치게 만들면 메시지가 반씩 갈린다.

## `sample/` 패키지

수직 슬라이스 검증용 일회용 코드다. 폐기할 때 main 패키지·테스트·Flyway 마이그레이션 **세 곳을 함께** 지운다(`apps/CLAUDE.md`).
