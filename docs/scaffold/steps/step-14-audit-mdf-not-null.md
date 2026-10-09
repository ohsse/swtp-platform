# Step 14 — 감사 수정 컬럼(`mdf_dttm`/`mdf_id`) NOT NULL 정정

- 일자: 2026-08-14
- 상태: ✅ 코드·DDL·문서 반영 완료 (컨테이너 E2E는 볼륨 리셋 후 별도 수행)
- 관련: [step-10-auth-slice.md](step-10-auth-slice.md)(원 결정), [step-06-master-slice.md](step-06-master-slice.md), 아키텍처 11.1 "공통 감사 컬럼 규약"

## 발단

Step 10에서 감사 컬럼 규약을 사내 표준(`rgstr_*`/`mdf_*`)으로 재정의하면서 **수정 컬럼을 nullable로** 두고
`mdf_dttm IS NULL`이 "한 번도 수정되지 않음"을 의미하도록 설계했다. 그러나 사내 표준의 실제 의미는 달랐다.

> 수정이 필요 없는 테이블에서는 `mdf_dttm`/`mdf_id` 컬럼을 **쓰지 않는다**.
> 컬럼을 둔 테이블은 **NOT NULL**이며, 등록 시점에 함께 등록된다.

즉 nullable 여부로 "미수정"을 표현하는 것이 아니라, **컬럼의 존재 여부**가 "이 테이블이 수정되는가"를
표현하는 축이었다. Step 10은 2단 규약(`BaseCreatedEntity` / `BaseEntity`)으로 그 축을 이미 정확히 세워 놓고도,
수정 컬럼을 nullable로 두어 같은 정보를 두 번 표현하고 있었다.

이 불일치는 이미 코드에 드러나 있었다. `V2__master_domain.sql`(DA# ERD 익스포트)의 실도메인 4개 테이블은
ERD 원본대로 `mdf_* NOT NULL`이었고, 그 파일 헤더에는 *"따라서 이 엔티티들은 `BaseEntity`를 상속할 수 없다"*는
경고가 달려 있었다. **ERD가 옳고 플랫폼 규약이 틀린 상태**였다.

## 결정 (2026-08-14)

> - **수정 컬럼은 NOT NULL이다.** 원칙은 "컬럼이 있으면 값이 반드시 있다" — "컬럼은 있는데 값이 NULL"인
>   상태는 존재하지 않는다. `@EnableJpaAuditing`의 `modifyOnCreate` 기본값(`true`)을 그대로 써서
>   등록 시점에 등록값과 동일하게 채운다.
> - **"한 번도 수정되지 않음"은 `mdf_dttm = rgstr_dttm`으로 판정한다.** Step 10이 nullable을 고수한 근거는
>   "등록값을 복사해 넣으면 미수정 정보가 복원 불가능하게 소실된다"였으나, 등록 컬럼이 `updatable=false`로
>   불변이므로 그 정보는 두 컬럼의 동등성에 그대로 보존된다. 소실되지 않는다.
> - **2단 규약과 `AuditorProvider` SPI는 그대로 유지한다.** 오히려 근거가 강해진다 — 수정 컬럼이 NOT NULL인
>   이상, 수정하지 않는 테이블에 형식적으로 붙이면 전 행이 `mdf_dttm = rgstr_dttm`인 무의미한 컬럼 두 개가 남는다.
> - **`getLastChangedDttm()`을 제거한다.** `COALESCE(mdf_dttm, rgstr_dttm)`은 이제 항상 `mdf_dttm`과 같다.
>   호출처가 없음을 확인하고 삭제했다.
> - **기존 Flyway 파일을 직접 수정한다.** 신규 ALTER 마이그레이션을 얹지 않는다 — 아직 스캐폴드 단계이고
>   `sample_*`은 일회용이라 이력을 깨끗하게 남기는 쪽이 낫다. 대가로 적용 절차에 `docker compose down -v`가
>   필수다(체크섬 불일치). Step 6이 같은 상황에서 V2 추가를 택했던 것과 반대 선택이며, 근거는
>   그때는 실도메인 DDL 추가였고 이번은 규약 자체의 정정이라 V1이 옛 규약을 서술한 채 남으면 안 되기 때문이다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `starters/.../SwtpJpaAuditingAutoConfiguration.java` | `@EnableJpaAuditing(modifyOnCreate = false)` → `@EnableJpaAuditing` (기본 `true`) |
| `starters/.../BaseEntity.java` | `mdf_dttm`/`mdf_id`에 `nullable = false`, `getLastChangedDttm()` 삭제, Javadoc 재작성(벌크 UPDATE 금지 명시) |
| `starters/.../BaseCreatedEntity.java` | 2단 규약 근거를 NOT NULL 전제로 다듬음 (코드 변경 없음) |
| `apps/auth-service/.../V1__auth_schema.sql` | 헤더 규약 블록 재작성 + `users`·`roles`·`permissions`·`refresh_tokens`·`jwt_signing_keys` 5개 테이블 `mdf_*` NOT NULL |
| `apps/auth-service/.../V2__default_admin.sql` | `roles`(3행)·`users`(1행) 시드 INSERT에 `mdf_dttm`/`mdf_id` 추가 (`LOCALTIMESTAMP`, `'SYSTEM'`) |
| `apps/master-service/.../V1__sample_item.sql` | 헤더 규약 블록 재작성 + `mdf_*` NOT NULL + 컬럼 코멘트 정정 |
| `apps/master-service/.../V2__master_domain.sql` | **DDL 무변경** — 무효가 된 "`BaseEntity` 상속 불가" 경고 헤더만 제거 |
| `apps/master-service/.../SampleItemDtos.java` | 응답 DTO Javadoc 정정 |
| `AuthSliceIntegrationTest` · `SampleItemSliceIntegrationTest` | 등록 직후 `mdf_* = rgstr_*` 동등성 단언으로 교체, 수정 후 갱신 단언 추가 |
| 문서 | 아키텍처 11.1 감사 컬럼 절, `plan.md` Phase 10 항목, step-06·step-10에 번복 각주 |

**손대지 않은 것**: `mdf_*` 컬럼이 없는 테이블(`user_roles`, `role_permissions`, `prcs_fclt_r`, `eqp_tag_p`)은
이미 규약을 정확히 따르고 있어 그대로 두었다. 감사 컬럼 규약 대상이 아닌 시계열·프레임워크 테이블
(`telemetry.*`, `job.*`, `quartz.*`, `batch.*`)도 무관하다. `rgstr_*` 체인과 `AuditorProvider` 배선은 불변이다.

## 함정 기록

- **시드 INSERT가 먼저 깨진다.** `V2__default_admin.sql`은 애플리케이션을 거치지 않으므로 `mdf_*`를
  직접 넣어야 한다. 빠뜨리면 마이그레이션 단계에서 즉시 실패한다 — 오히려 조기에 드러나는 안전한 실패다.
- **벌크 UPDATE 금지가 "이력 누락"에서 "제약 위반"으로 격상됐다.** 기존에도 JPQL `update`·네이티브 SQL은
  `AuditingEntityListener`를 우회해 감사 컬럼을 조용히 비웠지만(`TokenBreachService` 주석 참조),
  이제는 NOT NULL 제약에 걸리거나 이력이 옛 값에 멈춘다. 규약이 스스로를 강제하게 된 셈이다.
- **`modifyOnCreate=true`에서 두 시각은 정확히 같다.** Spring Data의 `AuditingHandler.markCreated()`는
  `touch(source, true)`로 등록·수정 값을 같은 시각 인스턴스에서 세팅한다. 따라서
  `mdf_dttm = rgstr_dttm` 판정은 근사 비교가 아니라 정확한 동등 비교다.
- **`V2__master_domain.sql`은 아직 git 미추적 상태였다.** DDL이 이미 NOT NULL이라 실질 변경이 없었고,
  헤더 경고만 제거했다.

## 알려진 한계

1. **컨테이너 E2E 미실행** — 기존 파일을 직접 수정했으므로 이미 적용된 개발 DB는 체크섬 불일치로 기동에 실패한다.
   `docker compose down -v` 후 재기동이 필수다. 아래 "검증" 참조.
2. **`tag_m`/`prcs_m`/`fclt_m`/`eqp_m`의 JPA 엔티티는 여전히 없다.** 이번 정정으로 이들이 `BaseEntity`를
   그대로 상속할 수 있게 됐지만, 엔티티·서비스·API를 붙이는 것은 실도메인 슬라이스의 몫이다.

## 검증

```bash
./gradlew :starters:swtp-persistence-starter:test    # Docker 불필요
./gradlew build                                      # 통합 테스트 포함 (Docker Desktop 필요)
```

컨테이너 검증:

```bash
docker compose -f infrastructure/docker/compose.yaml down -v   # 체크섬 불일치 회피 — 필수
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml up -d --build
```

제약이 실제로 걸렸는지 DB에서 직접 확인한다.

```sql
SELECT table_schema, table_name, column_name, is_nullable
  FROM information_schema.columns
 WHERE column_name IN ('mdf_dttm','mdf_id')
 ORDER BY table_schema, table_name;
-- 기대: 전 행 is_nullable = 'NO'
```

등록 시점 채움과 수정 시점 갱신을 엔드투엔드로 확인한다.

```sql
-- POST /api/sample-items 직후 → mdf_dttm = rgstr_dttm, mdf_id = rgstr_id (= 토큰 sub)
-- PUT  /api/sample-items/{id} 후 → mdf_dttm > rgstr_dttm, rgstr_* 는 불변
SELECT id, rgstr_dttm, rgstr_id, mdf_dttm, mdf_id FROM master.sample_item;
```
