# 01 — 제어그룹 도메인 DDL 도입과 영속 계층 신설

- 일자: 2026-08-24
- 상태: ✅ 완료 (`:apps:ems-service:build` 통과, 통합 테스트 9/9. 컨테이너 E2E 미실행 — 아래 「알려진 한계」 3)
- 검토 관점: 정수장 도메인 · 데이터 · 분산 아키텍처
- 리뷰: /code-review ✅ (3건) · 전용 ✅ (6건) — 전부 반영
- 관련: `docs/scaffold/steps/step-06-master-slice.md`(DDL 기준형), `docs/scaffold/steps/step-14-audit-mdf-not-null.md`(감사 컬럼 규약), `docs/scaffold/steps/step-20-integration-test-baseline-scope.md`(기준형 도입 시점), `apps/CLAUDE.md`「스타터 조합」·「통합 테스트 표준형」

## 발단

DA# ERD에 제어그룹 계열 5종이 신설됐다. EMS가 송수펌프 설비를 제어그룹 단위로 묶고,
그 펌프 제어에 따라 물이 어느 분기점으로 흐르는지를 수계통지점으로 표현한다.

ems-service는 step-20이 지목한 스캐폴드 3종(ems·pms·autonomous) 중 하나로 `EmsServiceApplication`
한 개뿐이었다. `ems` 스키마는 `infrastructure/postgresql/init/01-schemas.sql`이 이미 만들어 두고
자리를 비운 상태였고, 모듈 쪽에 `swtp-persistence-starter`가 없어 그 자리를 쓰지 않고 있었다.

따라서 이 작업은 마이그레이션 파일 하나가 아니라 **ems-service에 영속 계층을 처음 세우는 작업**이다.
step-20이 *"스캐폴드에 그 스타터를 붙이는 커밋이 기준형을 함께 만드는 커밋"* 이라고 시점을 못박아 둔
그 커밋이 이번 것이다.

## 결정 (2026-08-24)

### 1. 5종 전부 `ems` 스키마에 둔다

정수장 도메인 관점은 **반대**였다. 근거는 두 가지였다 — 아키텍처 4.3의 EMS 역할 6개에 "제어"라는
단어가 없고, `WNP_M`이 공정(`prcs_m`)·시설(`fclt_m`)과 다른 제3의 범용 위치 축(유량 수지·수질 감시·
누수 관리가 공유하는 계통 노드)이라 EMS가 참조할 뿐 소유할 것이 아니라는 것이었다.

분산 아키텍처 관점은 **조건부 찬성**이었다. 5종이 균질하지 않다고 봤다 — ems 자체 키만 쓰는
`ctrl_grp_m`·`wnp_m`·`ctrl_grp_wnp_p` 3종은 소유가 명확하나, `ctrl_eqp_p`는 PK가 `eqp_id` 단독이라
설비당 1행이므로 제어그룹의 하위 관계가 아니라 `master.eqp_m`의 1:1 확장 속성 테이블로 읽히고,
`apps/CLAUDE.md`의 교차 도메인 소유 규칙 ②("PK가 한쪽 도메인의 키 단독인 쪽이 소유")를 그대로
적용하면 소유자가 master로 판정된다는 것이었다.

**두 반대의 핵심 전제가 사실과 달랐다.** 여기서 제어그룹은 범용 제어 개념이 아니라 **EMS의 송수펌프
설비 제어 그룹**이고, 수계통지점은 범용 위치 마스터가 아니라 **그 펌프 제어의 결과로 물이 어느 분기로
가는지를 표현하는 EMS 제어 토폴로지**다. 즉 ems가 참조자가 아니라 **변경 주체**다.

문서가 이 판정을 뒷받침한다. 아키텍처 11.3은 제어 분담을 이미 축으로 갈라 놓았다 —
*"autonomous는 송수를 제외한 공정을, ems는 송수펌프·밸브를 제어"*. 4.3의 역할 목록에 "제어"가
없다는 지적은 읽은 범위의 한계였고, 같은 문장이 분산 아키텍처 관점이 우려한
"autonomous와 ems의 제어 대상 겹침"도 지운다. 겹치는 것은 제어 **이력**(`operation` 스키마의
명시적 예외)이지 제어 **구성**이 아니다.

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| 5종 전부 `master` | 제어그룹·수계통지점이 EMS 제어의 산물이라 master가 소유하면 변경 주체와 소유자가 어긋난다. master-service가 자기가 만들지도 바꾸지도 않는 데이터의 DDL을 지게 된다 |
| 3/2 분할 (`ctrl_eqp_p`·`ctrl_grp_tag_p`만 master) | 한 덩어리의 제어그룹 구성이 두 스키마로 쪼개진다. ems가 자기 제어그룹의 구성원을 조회하는 데 master HTTP 호출이 필요해지고, 편성 트랜잭션이 서비스 경계를 넘는다 |
| `operation` 스키마 | `operation`은 제어 **이력**(`control_command`·`operation_mode`)이고 "DDL은 master / write는 공용"이라는 특수 계약이다. 마스터성 구성 테이블을 얹으면 그 계약이 흐려진다 |

### 2. PK는 ERD 원본의 단독키를 유지한다

`ctrl_eqp_p`(PK=`eqp_id`)·`ctrl_grp_wnp_p`(PK=`wnp_id`)·`ctrl_grp_tag_p`(PK=`tag_sn`) 모두
ERD 원본의 단독키를 그대로 쓴다. 세 관점이 모두 "의도 확인 필요"로 지목했으나, `V2__master_domain.sql`이
세운 원칙이 *"ERD를 재익스포트해 대조할 수 있도록 원본 DDL의 형태를 그대로 유지한다"* 이다.
마이그레이션에서 임의로 고치면 DA# 원본과 코드가 조용히 갈라진다.

선례도 있다 — `master.eqp_tag_p`가 같은 단독키이고 V2가 *"PK가 `tag_sn` 단독인 것은 의도된 1:1 제약"*
이라고 명시해 뒀다. 단독키가 만드는 제약은 아래 「알려진 한계」 1번에 남긴다.

### 3. 영속 배선은 JPA로 하고, 통합 테스트 기준형을 같은 커밋에서 만든다

`swtp-persistence-starter`는 JPA를 전파하지 않으므로(`compileOnly`) 앱이 `spring-boot-starter-data-jpa`를
직접 선언한다. persistence-starter를 쓰는 4종(master·telemetry·job·auth)이 예외 없이
`swtp-security-starter`도 함께 갖는데, 감사 주체(`rgstr_id`/`mdf_id`)를 토큰 `sub`로 채우는
`AuditorProvider` 구현이 거기 있기 때문이다. 없으면 전 행이 `SYSTEM`으로 채워진다.
ems도 같은 조합을 따른다.

기준형이 그 testFixtures의 `SwtpTestJwt`를 쓰는 것은 master·telemetry·job 3종이고,
**auth-service는 예외다** — 자기 DB 서명키로 발급과 검증을 같은 프로세스에서 하므로
테스트용 키 주입이 필요 없다. ems는 앞의 3종 쪽이다.

Kafka는 붙이지 않는다 — 이번 범위에 이벤트가 없다. 따라서 기준형은 **PG 컨테이너만** 띄우는
auth-service 형태를 복제한다(`apps/CLAUDE.md`: "띄우는 컨테이너는 의존한 스타터와 일치시킨다").

### 4. 마이그레이션은 `V1`부터 시작한다

master가 `V2__master_domain.sql`부터인 것은 관례가 아니라 사고의 산물이다 — step-06이
*"개발 DB에 이미 V1이 적용돼 있어, 교체하면 Flyway 체크섬 불일치로 기동이 실패한다"* 며 밀어 넣은
결과다. ems 스키마에는 아직 아무 테이블도 없으므로 따라할 이유가 없다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `apps/ems-service/src/main/resources/db/migration/V1__ems_domain.sql` | 신규 — 테이블 5종 |
| `apps/ems-service/build.gradle` | persistence·security 스타터 + `data-jpa` + `validation` + 테스트 의존 4종 추가 |
| `apps/ems-service/src/main/resources/application.yml` | `flyway.schemas`/`default-schema`, `jpa.properties.hibernate.default_schema` 추가 |
| `apps/ems-service/src/test/.../AbstractIntegrationTest.java` | 신규 — PG 컨테이너만 (auth-service 형태) |
| `apps/ems-service/src/test/.../EmsMigrationIntegrationTest.java` | 신규 — V1 적용 결과 검증 |
| `apps/ems-service/src/test/.../EmsServiceApplicationTest.java` | **삭제** — persistence-starter와 공존 불가(아래 「함정 기록」 1) |
| `infrastructure/docker/compose.yaml` | ems-service `depends_on`에 `*depends-db` 추가 (리뷰 반영) |
| `apps/CLAUDE.md` | 「스타터 조합」 표와 「통합 테스트 표준형」에서 ems를 스캐폴드 목록에서 제외 (리뷰 반영) |
| `apps/ems-service/CLAUDE.md` | 「현재 상태 — 스캐폴드」 절 갱신 |
| **master-service** | **변경 없음** — 5종 전부 ems 소유 |
| **`infrastructure/postgresql/init/01-schemas.sql`** | **변경 없음** — `ems` 스키마가 이미 있다 |
| **`apps/CLAUDE.md`의 소유 스키마 표** | **변경 없음** — `ems-service → ems`가 이미 적혀 있다 (같은 파일의 다른 두 절은 위 표대로 수정) |
| **`config-repo/`** | **변경 없음** — 접속정보·`create-schemas: false`는 이미 전역 |

## 구현 상세

### ERD 원문에서 정정한 6가지

기준형 `apps/master-service/.../V2__master_domain.sql` 대비.

| # | 정정 | 근거 |
|---|---|---|
| 1 | 식별자를 소문자 무따옴표로 | V2 헤더 — PG가 소문자로 폴딩하므로 생성 결과는 동일 |
| 2 | 스키마명 하드코딩 안 함 | `spring.flyway.default-schema=ems`가 대상을 결정 |
| 3 | `DROP TABLE IF EXISTS … CASCADE` 전부 제거 | `V1__quartz_schema.sql` 선례 — "원본의 DROP 블록은 마이그레이션 부적합으로 제거". 버전 마이그레이션은 1회 적용·체크섬 고정이라 멱등성이 불필요하고, `CASCADE`는 재적용 사고 시 파괴 범위가 테이블 하나를 넘는다 |
| 4 | PK 인덱스명 `_pkey` → `_u_idx` | V2가 2단계 PK 생성 시 PG의 자동 개명을 전제로 적어 둔 표기. 결과는 `_pkey` 하나로 같다 |
| 5 | FK 제약 없음 | V2 — "참조 정합성은 애플리케이션 책임". ems 내부 참조까지 포함 |
| 6 | `ctrl_grp_tag_p` 컬럼 순서 정정 | 원문이 감사 컬럼 사이에 `sort_ord`를 끼워 뒀다. 업무컬럼 → `rgstr_*` → `mdf_*` 순으로 정렬 |

### 감사 컬럼과 엔티티 기반 클래스

step-14 규약("컬럼이 있으면 NOT NULL, 수정하지 않는 테이블은 컬럼을 두지 않는다") 대비 5종 모두 일치한다.

| 테이블 | 감사 컬럼 | 상속할 기반 클래스 |
|---|---|---|
| `ctrl_grp_m` | 4종 | `BaseEntity` |
| `wnp_m` | 4종 | `BaseEntity` |
| `ctrl_eqp_p` | `rgstr_*`만 | `BaseCreatedEntity` |
| `ctrl_grp_wnp_p` | 4종 | `BaseEntity` |
| `ctrl_grp_tag_p` | 4종 | `BaseEntity` |

`ctrl_eqp_p`만 `mdf_*`가 없는 것은 규약상 모순이 아니다 — 컬럼 유무 자체가 "이 테이블을 수정하는가"의
선언이므로 세 `_P`가 서로 다른 선언을 하는 것은 허용된다. `master.eqp_tag_p`(rgstr만)와 완전 동형이다.
다만 「알려진 한계」 2번을 함께 볼 것.

## 함정 기록

1. **기존 `EmsServiceApplicationTest`를 함께 지워야 했다.** 맨 `@SpringBootTest`라 DataSource가 없는데,
   persistence-starter가 붙는 순간 Flyway가 기동 시점에 DB를 찾아 컨텍스트 로드부터 실패한다.
   step-20이 관찰한 대응(스타터 쓰는 5개는 `AbstractIntegrationTest`, 안 쓰는 6개는 `*ApplicationTest`)이
   **예외 없이 성립하는 이유가 여기 있었다** — 둘은 공존하는 것이 아니라 교체되는 것이다.
   master·auth도 실도메인 착수 때 같은 교체를 했고, 이번에 ems가 6/6에서 5/5 쪽으로 넘어가
   대응이 7/7 ↔ 5/5가 아니라 **5/5 ↔ 6/6에서 6/6 ↔ 5/5로** 유지됐다.

2. **`use_yn` 기본값 단언을 PG 표현에 맞춰야 했다.** `information_schema.columns.column_default`가
   `DEFAULT 'Y'`를 `'Y'::bpchar`로 돌려준다 — `CHARACTER(1)`이라 `bpchar` 캐스트가 붙는다.
   문자열 동등 비교로 쓰면 타입을 바꿀 때 조용히 깨지므로 `LIKE '''Y''%'`로 느슨하게 걸었다.

3. **`ANY(?)` 바인딩에는 배열을 넘겨야 한다.** `pg_indexes` 조회에서 `List`를 그대로 주면
   드라이버가 배열로 승격하지 않는다. `TABLES.toArray(String[]::new)`로 넘긴다.

### 리뷰에서 나와 고친 것

4. **`compose.yaml`의 ems 블록에 `*depends-db`가 없었다.** [/code-review 높음]
   변경 내역표를 쓸 때 인프라를 "변경 없음"으로 단정하고 **후보에조차 올리지 않은 것**이 원인이다.
   ems가 DB를 쓰지 않던 시절의 `depends_on`이 그대로 남아 있었다. 실패 경로는 조용하지 않다 —
   볼륨을 지운 콜드 스타트에서 `01-schemas.sql` 실행으로 늦어진 DB보다 ems가 먼저 뜨고,
   Flyway 커넥션 실패로 컨텍스트가 죽고, `restart` 정책이 없어 그대로 종료되고,
   gateway가 `ems-service: service_healthy`를 영영 기다려 **스택 전체가 올라오지 않는다.**
   교훈: "이 서비스가 무엇을 새로 필요로 하게 됐는가"를 물으면 compose가 후보에 오른다.

5. **`mdf_*` 단언이 공허 참이었다.** [전용 리뷰]
   `WHERE column_name IN ('mdf_dttm','mdf_id') AND is_nullable='YES'`가 비었는지만 봤는데,
   이 쿼리는 **컬럼이 아예 없어도 빈 결과**다. 네 테이블에서 `mdf_*`가 통째로 사라져도 초록이었다.
   존재 단언(`MODIFIABLE_TABLES` 루프)을 앞에 두어 고쳤다.
   같은 파일의 `등록_감사_컬럼이_전_테이블에_있다()`는 처음부터 루프로 존재까지 단언하고 있었다 —
   **한 테스트 클래스 안에서 단언 강도가 갈려 있었던 것**이 놓친 이유다.

6. **"한글 코멘트"를 검증한다고 써 놓고 코멘트 존재만 검사했다.** [전용 리뷰]
   `@DisplayName`과 문서가 "한글"이라고 주장하는데 단언은 `col_description(...) IS NULL` 뿐이라
   영문 코멘트도 통과했다. 루트 불변식 7이 DDL 코멘트에도 걸리므로 한글 포함까지 단언하도록 고쳤다.

   **고치는 과정에서 같은 함정을 한 번 더 밟았다.** 처음엔 `!~ '[가-힣]'`가 빈 결과인지로 썼는데,
   그것도 "전부 한글일 때"와 "정규식이 아무것도 못 잡을 때"가 구별되지 않는 부정형 단언이다.
   한글을 가진 컬럼 수가 **전체 컬럼 수와 같은지** 대조하고 `isGreaterThan(0)`을 붙여,
   정규식이 실제로 매칭하고 있다는 것까지 함께 증명하게 바꿨다.
   **부정형 단언은 자기가 헛돌고 있는지를 스스로 말해 주지 않는다.**

7. **변경 내역표에 하지 않은 변경을 적었다.** [양쪽 리뷰]
   `build.gradle`에서 `failOnNoDiscoveredTests` 블록을 제거했다고 적었으나 ems에는 그 블록이
   **존재한 적이 없다**(master-service의 것이다). 반대로 실제로 지운 `EmsServiceApplicationTest`는
   표에 없었다. 둘 다 정정했다.

8. **`apps/CLAUDE.md`를 갱신 대상에서 빠뜨렸다.** [양쪽 리뷰]
   소유 스키마 표만 확인하고 「통합 테스트 표준형」 절을 점검하지 않았다.
   그 절이 ems를 "아직 스캐폴드"로 분류한 채 `*ApplicationTest`만 두라고 지시하고 있었다 —
   다음 개발자가 그대로 따르면 위 함정 1을 재현한다. 두 곳을 최소 수정했다.

9. **auth-service를 근거에 잘못 끌어왔다.** [전용 리뷰]
   "4종이 기준형에서 `SwtpTestJwt`를 쓴다"고 적었으나 auth-service는 `testFixtures` 의존이 없다 —
   자기 DB 서명키로 발급·검증을 같은 프로세스에서 하기 때문이다. 결론은 나머지 3종만으로 서지만
   근거 문장이 사실과 달랐다. 정정했다.

## 알려진 한계

1. **`ctrl_eqp_p`의 단독키 PK가 한 펌프의 다중 그룹 소속을 막는다.** 정수장 도메인 관점이
   *"송수펌프 1대는 에너지 최적화 그룹(심야전력 부하이전)과 수질·비상 대응 그룹에 동시 소속되는 것이 정상"*
   이라고 지적했다. ERD 원본 유지를 택했으므로 **고치지 않았다**(위 결정 2).
   지금은 송수펌프 편성이 EMS 단독 관할이라 실害가 없다. 완화가 필요해지면 ERD를 먼저 고치고
   재익스포트한다 — 되돌리는 방향(단독키 → 복합키)은 제약을 푸는 쪽이라 데이터 손실이 없다.
   반대로 처음부터 복합키로 두면 나중에 조일 때 이미 쌓인 중복을 정리해야 한다.

2. **`sort_ord` 재정렬을 UPDATE로 하면 `ctrl_eqp_p`에서 감사 흔적이 남지 않는다.**
   `mdf_*`가 없는 것은 "이 테이블을 수정하지 않는다"는 선언이므로, 서비스 계층이
   **delete-then-insert**로 구현되어야 일관된다. UPDATE 재정렬을 할 계획이면
   `ctrl_eqp_p`에도 `mdf_*` NOT NULL을 추가해 `BaseEntity`로 올려야 한다.
   ERD 작성 의도가 어느 쪽인지는 확인되지 않았다.

3. **➖ 검증불가 — 컨테이너 E2E.** `docker compose up` 경로는 실행하지 않았다.
   Testcontainers가 `spring.flyway.create-schemas=true`로 스키마를 직접 만드는 것과 달리,
   운영 경로는 `01-schemas.sql`이 만든 `ems` 스키마에 `create-schemas: false`로 붙는다.
   **그 경로는 이번 검증이 재현하지 못한다.** 다만 `ems` 스키마는 이 파일이 이미 만들고 있었고
   (`-- ems-service 소유`) 다른 4개 서비스가 같은 배선으로 동작 중이다.

4. **`ctrl_eqp_p.eqp_id`·`ctrl_grp_tag_p.tag_sn`의 유효성 검증 장치가 아직 없다.**
   master 소유 키의 로컬 복제본인데 FK가 없고 스키마도 다르다. 그물은 애플리케이션뿐이며,
   그 애플리케이션 코드(엔티티·서비스)가 이번 범위에 없다. master가 물리 삭제 API를 갖지 않고
   `use_yn='N'` 소프트 삭제만 하므로 고아 행은 조회 시점 필터로 흡수되나,
   **INSERT 시점의 존재 확인은 다음 단계에서 붙여야 한다.**

5. **`*depends-db` 추가를 실행으로 확인하지 못했다.** `compose.yaml` 수정은 다른 4개 서비스와
   같은 앵커를 붙인 것이라 형태상 확실하나, 이번 작업에서 컨테이너를 띄우지 않았으므로
   **한계 3과 같은 `➖ 검증불가`다.** 볼륨을 지운 콜드 스타트가 실제 재현 조건이다.

6. **ems가 `*depends-kafka`를 그대로 갖고 있다.** `swtp-kafka-starter`를 의존하지 않으므로
   기다릴 이유가 없고 기동만 늦어진다. 이번 변경이 만든 것이 아니라 이전부터 있던 상태라
   범위 밖으로 두었다 — 실해는 없다. 이벤트를 붙이지 않기로 확정되면 정리 대상이다.

7. **`apps/CLAUDE.md`를 두 곳만 고쳤다.** `/step`은 CLAUDE.md 정합성을 `/claude-md-sync`의
   영역으로 넘기는데, 이번 커밋이 참이던 문장을 거짓으로 만든 자리라 최소 수정했다
   (「스타터 조합」 표 1행, 「통합 테스트 표준형」 절). **전면 점검은 하지 않았다** —
   같은 파일의 「패키지 규약」 표에서 ems의 A형/B형 판정이 비어 있는 것 등은 그대로 두었다.

## 검증

```bash
./gradlew :apps:ems-service:build
```

Testcontainers PG(`timescale/timescaledb-ha:pg17`)에서 V1이 적용되고 다음이 참인지 확인한다.

1. 테이블 5종이 `ems` 스키마에 존재한다
2. PK가 ERD 원본대로 단독키다 (`ctrl_eqp_p`=`eqp_id`, `ctrl_grp_wnp_p`=`wnp_id`, `ctrl_grp_tag_p`=`tag_sn`)
3. `mdf_dttm`/`mdf_id`가 전부 `NOT NULL`이고, `ctrl_eqp_p`에는 그 컬럼이 없다
4. 테이블·컬럼 한글 코멘트가 전부 달려 있다
5. FK 제약이 하나도 없다
6. Flyway 히스토리 테이블이 `ems` 스키마 안에 있다
7. `verifyIntegrationTestBaseline` 태스크가 통과한다

### 실측 결과 (2026-08-24, 리뷰 반영 후 재실행)

```
> Task :apps:ems-service:verifyIntegrationTestBaseline
> Task :apps:ems-service:test
> Task :apps:ems-service:check
> Task :apps:ems-service:build

BUILD SUCCESSFUL
```

Flyway 적용 로그 — Testcontainers PG에서 뽑은 원문이다. **읽기 좋게 재배열하지 않는다**;
순서와 실행시간을 손대면 XML 원본과 대조할 수 없게 된다(리뷰 지적으로 교체).

```
Database: jdbc:postgresql://localhost:2178/test?loggerLevel=OFF (PostgreSQL 17.10)
Creating schema "ems" ...
Migrating schema "ems" to version "1 - ems domain"
will rename index "ctrl_grp_m_u_idx" to "ctrl_grp_m_pkey"
will rename index "wnp_m_u_idx" to "wnp_m_pkey"
will rename index "ctrl_eqp_p_u_idx" to "ctrl_eqp_p_pkey"
will rename index "ctrl_grp_wnp_p_u_idx" to "ctrl_grp_wnp_p_pkey"
will rename index "ctrl_grp_tag_p_u_idx" to "ctrl_grp_tag_p_pkey"
Successfully applied 1 migration to schema "ems", now at version v1 (execution time 00:00.080s)
```

`will rename index` 5줄이 **2단계 PK 생성의 직접 증거**다 — `_u_idx`로 만든 인덱스가
제약 이름으로 흡수되어 적용 후에는 `_pkey`만 남는다는 것을 PG가 스스로 말하고 있다.

테스트 결과 — `build/test-results/test/TEST-com.mo.swtp.ems.EmsMigrationIntegrationTest.xml`:

```
tests="9" skipped="0" failures="0" errors="0"
```

| 검증 항목 | 테스트 메서드 | 실제 단언하는 것 | 결과 |
|---|---|---|---|
| 1. 테이블 5종 존재 | `테이블_5종이_생성된다` | `information_schema.tables`가 정확히 5종 | ✅ |
| 2. 단독키 PK | `PK가_ERD_원본대로_단독키다` | `pg_constraint` contype='p'의 컬럼이 테이블마다 정확히 1개 | ✅ |
| 2'. `_u_idx` → `_pkey` 개명 | `PK_인덱스가_제약_이름으로_개명된다` | `pg_indexes` 5건이 전부 `_pkey`로 끝남 | ✅ |
| 3. `mdf_*` NOT NULL / `ctrl_eqp_p` 부재 | `감사_수정_컬럼_규약을_지킨다` | 4종에 **존재**하고, `ctrl_eqp_p`에는 없고, nullable이 0건 | ✅ |
| 3'. `rgstr_*` 전 테이블 NOT NULL | `등록_감사_컬럼이_전_테이블에_있다` | 5종 루프 존재 확인 + nullable 0건 | ✅ |
| 4. 한글 코멘트 | `한글_코멘트가_빠짐없이_달려_있다` | 코멘트 없는 것 0건 + **한글 포함 컬럼 수 = 전체 컬럼 수(>0)** + 테이블 5종 | ✅ |
| 5. FK 제약 없음 | `FK_제약이_없다` | `pg_constraint` contype='f' 0건 | ✅ |
| 6. 히스토리 테이블 위치 | `히스토리_테이블이_자기_스키마에_있다` | `flyway_schema_history`가 `ems` 단독 | ✅ |
| 7. 규약 태스크 | `verifyIntegrationTestBaseline` | Gradle `check` 의존으로 실행 | ✅ |
| (추가) `use_yn` 기본값 | `사용여부_기본값이_걸려_있다` | `column_default LIKE '''Y''%'`인 테이블이 `ctrl_grp_m`·`wnp_m` | ✅ |

**"무엇을 통과했는지" 열을 따로 둔 이유**가 있다. 첫 작성 때 항목 3과 4는 ✅였지만
실제 단언은 주장보다 좁았다 — 3은 컬럼이 사라져도 통과하는 공허 참이었고,
4는 영문 코멘트도 통과했다(「함정 기록」 5·6). **`tests="9" failures="0"`은 그것을 알려주지 않는다.**

`BUILD SUCCESSFUL`과 테스트가 실제로 돈 것은 다르므로 XML 리포트의 `tests="9"`를 함께 붙인다.

## 다음 단계

1. **JPA 엔티티·REST API 슬라이스** — 이번 범위에서 뺐다. `apps/CLAUDE.md`의 패키지 규약
   B형/A형 판정이 먼저 필요하다(`ctrl`과 `wnp`가 각각 독립 CRUD 표면과 자기 테이블을 가지면 A형 2단).
   복제 대상인 master-service 도메인 슬라이스가 지금 패키지 리팩터링 중이므로 그것이 끝난 뒤가 낫다.
   엔티티를 붙일 때 「알려진 한계」 2번(delete-then-insert 여부)과 4번(master 키 존재 확인)을 함께 결정한다.

2. **master-service의 사라진 `V1__sample_item.sql`** — 이번 조사 중 발견한 별건이다.
   `db/migration/`에 `V2__master_domain.sql`만 있는데 step-06이 V2를 택한 근거가
   *"개발 DB에 이미 V1이 적용돼 있어서"* 였다. **V1이 적용된 개발 DB에서는 Flyway `validate`가
   missing migration으로 master-service 기동을 세운다.** `docker compose down -v` 또는
   `ignore-migration-patterns`가 필요하다. 별도 `/step` 대상.
