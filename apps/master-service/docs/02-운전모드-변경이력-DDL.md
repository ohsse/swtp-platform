# 02 — 운전모드 변경이력 DDL과 `operation` 스키마 개설

- 일자: 2026-08-24
- 상태: ✅ 완료 (`:apps:master-service:build` 통과, 통합 테스트 10/10. 운영 경로 E2E 미실행 — 「알려진 한계」 3)
- 검토 관점: 없음 — 소환하지 않음(사유는 아래 「검토 관점을 소환하지 않은 이유」)
- 리뷰: /code-review ✅ (6건) · 전용 ✅ (13건, 그중 2건은 기각 — 「함정 기록」 8)
- 관련: `apps/ems-service/docs/01-제어그룹-도메인-DDL.md`(직전 동형 작업·정정 규칙의 원천), `apps/master-service/docs/01-패키지-규약.md`(그 문서의 알려진 한계 5 · 다음 단계의 `failOnNoDiscoveredTests` 항목을 이번에 해소), `docs/architecture/스마트정수장_리빌드_아키텍처.md` 11.3(공용 write 예외), `docs/scaffold/steps/step-06-master-slice.md`(DDL 기준형), `docs/scaffold/steps/step-14-audit-mdf-not-null.md`(감사 컬럼 규약)

## 발단

`operation` 스키마는 `infrastructure/postgresql/init/01-schemas.sql`이 만들어 두고,
`apps/master-service/src/main/resources/application.yml`이 `spring.flyway.schemas: master,operation`으로
DDL 소유를 선언해 둔 채 **테이블이 하나도 없는 상태**였다. 아키텍처 11.3이 예고한 두 테이블
(`control_command`·`operation_mode`) 중 어느 것도 아직 없다.

DA# ERD에 `DRVMD_CHG_H`(운전모드변경) 엔티티가 신설되면서 그 자리를 처음 쓰게 됐다.
EMS는 제어그룹 단위로, 자율운영은 공정 단위로 운전모드를 잡으며, 그 전환 이력을 한 곳에 모은다.

따라서 이 작업은 마이그레이션 파일 하나가 아니라 **`operation` 스키마를 처음 여는 작업**이고,
"DDL은 master / write는 공용"이라는 특수 계약이 실제 테이블 위에서 처음 성립하는 지점이다.

## 검토 관점을 소환하지 않은 이유

`/step` 판정표상 "새 테이블·스키마·Flyway 마이그레이션"은 데이터 관점 1명 소환 대상이다.
소환하지 않기로 사용자와 합의했다 — 착수 전 대화 3라운드가 이미 데이터 관점 심층 토론이었고
(스키마 소유권, 다형 참조 판별자, 감사 로그 비정규화, 조회 인덱스), 범위가 테이블 하나다.

**그 대신 조사 순서를 지킨 것이 값을 했다.** 코드(`db/migration/`)만 봤다면 `operation`이 비어 있으니
자유 설계로 판단했을 것이다. 결정 이력을 먼저 읽어 ERD가 SSOT라는 것과 표준단어사전의 존재를
확인했고, 그 결과 초기 제안 컬럼명 하나가 표준 위반이었음이 드러났다(아래 결정 6).

## 결정 (2026-08-24)

### 1. `operation` 스키마에 두고, DDL은 master가 소유하되 write는 각 서비스가 한다

아키텍처 11.3의 명시적 예외를 그대로 따른다. 이력을 서비스별 스키마로 나누면
"이 대상을 방금 누가 건드렸나"가 스키마 경계로 쪼개져 사고 조사가 성립하지 않는다.

**write를 master에 모으지 않는 것이 이 결정의 핵심이다.** master가 REST로 이력을 대신 받아 쓰면
모드 전환은 성공했는데 이력 기록은 실패하는 창이 생긴다 — 감사 로그의 가장 치명적 실패 모드다.
11.3이 "명령과 같은 트랜잭션에 동기 기록", "Kafka로 이력 자체를 비동기 적재하지 않는다"로
못박아 둔 것과 같은 이유다. `iss_svc_cd`도 각 서비스가 자기 트랜잭션에서 자기 코드를 박을 때만
신뢰할 수 있는 값이다 — 대행 기록이면 호출자가 넘기는 위조 가능한 파라미터가 된다.

읽기(통합 조회)는 master가 맡는다. **쓰기는 분산, 읽기는 집중이다.**

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| `ems` 스키마에 제어그룹 이력을 둔다 | ems가 자기 마이그레이션을 굴릴 수 있어 편하지만, 그 편의가 `operation`이 존재하는 이유를 정확히 무효화한다. 사고는 공정 경계를 넘어 전파되므로, UNION해서 시간순 정렬해야 하는 감사 로그는 이미 실패한 것이다 |
| master가 이력 write API를 제공하고 각 서비스가 호출 | 위 본문 참조. 트랜잭션 경계가 갈라지고 `iss_svc_cd`가 위조 가능해진다 |
| 이력을 Kafka로 발행해 비동기 적재 | 11.3이 명시적으로 금지. 감사 데이터는 정합성이 지연 흡수보다 우선한다 |

### 2. ERD 원본에 실질 변경 4건을 가한다 — SSOT 역전을 문서로 막는다

`DRVMD_CHG_H`는 ERD에 이미 있고 주어진 DDL은 그 익스포트다. 그런데 아래 4건은 ERD에 없다.

| # | 변경 | 근거 |
|---|---|---|
| 1 | `mdf_dttm`·`mdf_id` **삭제** | step-14 규약 — "수정하지 않는 테이블에는 그 컬럼을 두지 않는다. 컬럼의 유무가 곧 그 선언이다". 이력 테이블에 수정 컬럼은 append-only 감사 로그로서 자기모순이다 |
| 2 | `ctrl_trgt_type_cd` **추가** | 결정 3 |
| 3 | `ctrl_trgt_nm` **추가** | 결정 4 |
| 4 | `chg_rsn_cd`·`chg_rsn_rmrk` **추가** | 결정 5 |

**이것은 ems 01의 결정 2가 경계한 것과 같은 종류의 일이다.** 그 문서는
"마이그레이션에서 임의로 고치면 DA# 원본과 코드가 조용히 갈라진다"며 ERD 원본 유지를 택했다.
차이는 두 가지다 — 그때 기각된 것은 에이전트의 제안이었고 이번은 사용자 본인의 결정이며,
그때의 정정 6건은 **형식**(소문자·DROP 제거·인덱스명·FK·컬럼 순서)이었고 이번은 **컬럼 추가/삭제**다.
급이 다르다는 것을 숨기지 않는다.

따라서 ERD 원본 유지 대신 **"코드가 앞서고 ERD가 따라온다"**를 택하되, 그 문서가 경계한
"**조용히** 갈라짐"의 *조용히* 를 두 겹으로 막는다.

1. 마이그레이션 헤더에 「ERD 원문에서 실질 변경한 것」 표를 둔다 — 재익스포트 대조 시 즉시 보인다
2. 아래 「알려진 한계」 1번에 DA# 재익스포트 필요를 남긴다

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| ERD를 먼저 고치고 재익스포트한 뒤 착수 | 정석이고 SSOT가 완벽히 유지된다. `.damx`가 DA# 전용 바이너리라 이 세션에서 편집할 수 없어 작업이 중단되므로 택하지 않았다. **차선이 아니라 순서 문제**이며, 한계 1번이 그 순서를 되돌린다 |
| ERD 원본 그대로 V3 작성, 확장은 V4로 | 정합은 완벽하나 이력 테이블에 `mdf_*`가 남고, 사유·대상유형이 비어 있는 이력이 그 사이 영구히 쌓인다. 이력 데이터는 스키마를 나중에 고쳐도 **과거를 소급할 수 없다** — 이 비대칭이 기각 사유다 |

### 3. 제어대상은 다형 참조 — 판별자를 `iss_svc_cd`에 겸임시키지 않는다

`ctrl_trgt_id` 하나가 EMS에서는 `ems.ctrl_grp_m.ctrl_grp_id`를, 자율운영에서는
`master.prcs_m.prcs_id`를 가리킨다. 두 값의 폭이 모두 `VARCHAR(36)`이라 형태는 맞는다.

판별을 `iss_svc_cd`로 하자는 안이 먼저 나왔으나 **별도 `ctrl_trgt_type_cd`**를 둔다.

- 11.3이 정의한 `iss_svc_cd` 값에는 `MANUAL`이 있다. 운전원이 HMI에서 직접 모드를 바꾸는
  경우 — 자율운영 시스템에서 가장 중요하게 기록될 이벤트 — 에 대상이 그룹인지 공정인지 알 수 없다
  → **이 근거는 이후 무효가 됐다.** 운전모드에는 현장 직접 조작 경로가 없어 `MANUAL`을 두지 않기로 했고
  (`03`의 결정 10), 지금 두 컬럼은 값이 1:1이다. **결론은 유지되나 근거가 바뀌었다** — 03을 볼 것
- 두 컬럼의 축이 다르다. `iss_svc_cd`는 *책임 추적*, `ctrl_trgt_type_cd`는 *참조 해석*이다.
  지금 1:1로 겹치는 것은 우연이며, EMS가 설비 단위 모드를 갖거나 자율운영이 그룹 단위를 다루면 깨진다.
  **이미 쌓인 이력은 그 시점에 소급 해석이 불가능하다**
- 이 프로젝트는 FK를 걸지 않는다(V2 헤더 — 참조 정합성은 애플리케이션 책임).
  DB가 잡아주지 않으므로 컬럼 자체가 자기설명적이어야 한다
- 비용이 `VARCHAR(10)` 한 칸이다

### 4. 대상명 스냅샷을 남긴다 — master가 스키마 경계를 넘지 않기 위해

조인 대상이 스키마를 넘는다는 것이 조사 중 드러났다.

| 대상 | 테이블 | 스키마 | 소유 |
|---|---|---|---|
| 제어그룹 | `ctrl_grp_m` | **ems** | ems-service |
| 공정 | `prcs_m` | **master** | master-service |

master가 통합 조회를 맡는데 `ems.ctrl_grp_m`을 직접 읽으면 그것이 소유권 원칙 위반이다.
`operation`은 명시적 예외로 승인된 것이지 `ems`까지 열어준 것이 아니다.

`ctrl_trgt_nm`을 이력 행에 함께 남기면 master는 조인 없이 이력만 읽어 반환한다.
**감사 로그에서의 비정규화는 안티패턴이 아니라 요구사항이다** — 기준정보가 개명되거나
`use_yn='N'`으로 소프트 삭제돼도 "그 당시 그 대상의 이름"이 보존돼야 조사가 성립한다.

### 5. 전환 사유를 필수로 한다

`chg_rsn_cd`를 NOT NULL로 둔다. "운전원이 AI 자동을 왜 껐나"는 사고 조사의 첫 질문이고
`bf`/`af` 코드만으로는 답이 나오지 않는다. 초기 코드 집합은 최소로 시작해 확장한다 —
`OPRTR`(운전원판단) · `ANOMALY`(이상감지) · `ITLCK`(인터록) · `SCHED`(스케줄).
서술이 필요한 경우를 위해 `chg_rsn_rmrk`(비고)를 NULL 허용으로 함께 둔다.

코드값을 DB 제약(CHECK)으로 걸지 않는다 — V2·V1 어디에도 CHECK 선례가 없고,
`prcs_type_cd`·`fclt_type_cd` 등 기존 코드 컬럼이 전부 제약 없이 애플리케이션 책임이다.

### 6. 컬럼명은 DA# 표준단어사전에 맞춘다

`docs/erd/스마트정수장.db`는 ERD가 아니라 **DA# 표준단어사전**이다(SQLite, `STD_DIC` 29,644건).
컬럼명은 여기 등재된 단어의 조합이어야 한다. 조사에서 이것이 드러나 초기 제안 하나를 정정했다.

| 요소 | 표준단어 | 판정 |
|---|---|---|
| 제어·대상·유형·코드 | `CTRL`·`TRGT`·`TYPE`·`CD` | ✅ 전부 등재 |
| 명 | `NM` | ✅ |
| 사유 | `RSN` | ✅ |
| 비고 | **`RMARK`** → **`RMRK`** | ❌→✅ 초기 제안 `rm`을 정정. **`RM`은 사전에서 Relationship Manager이지 비고가 아니다**. → **이후 `RMRK`로 다시 바뀌었다**(2026-08-25): `STD_DIC`에 `('비고','RMRK')`가 새로 등재됐다. 사전에 '비고'가 `RMARK`·`RMRK` 두 벌로 존재하게 된 상태이며 `RMRK`는 '특이사항'과도 겹친다 — `04`의 알려진 한계 7 |

**ERD 자신도 표준과 어긋난 데가 있다.** 사전상 변경은 `CHNG`이고 `CHG`는 "교체"인데
엔티티명이 `DRVMD_CHG_H`다. ERD가 SSOT이므로 `CHG`를 따르되 사실만 기록한다(한계 2번).

### 7. 비유니크 인덱스 명명 관례를 세운다

지배적 조회는 "사고 시각에 이 대상이 무슨 모드였나"이므로 `(ctrl_trgt_id, rgstr_dttm DESC)`가 필요하다.
그런데 ERD 계열 파일(master V2 · ems V1)에는 **비유니크 인덱스 선례가 없다** — 전부 PK용 `_u_idx`뿐이다.
`auth`의 `ix_refresh_tokens_user`가 유일한 자체 선례이나 ERD 계열과 표기 체계가 다르다.

`_u_idx`(unique)와 대칭되도록 **`_i_idx01`**(index)로 두고, master V2의 `_u_idx01`/`_u_idx02`
번호 부여 관례를 따른다. 다음 ERD 계열 비유니크 인덱스가 참조할 선례가 된다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `apps/master-service/src/main/resources/db/migration/V3__operation_drvmd_chg.sql` | 신규 — `operation.drvmd_chg_h` 1종 |
| `apps/master-service/src/test/java/com/mo/swtp/master/OperationMigrationIntegrationTest.java` | 신규 — 10건 |
| `apps/master-service/build.gradle` | `failOnNoDiscoveredTests = false` 블록 **제거** |
| **`application.yml`** | **변경 없음** — `schemas: master,operation`이 이미 있다 |
| **`infrastructure/postgresql/init/01-schemas.sql`** | **변경 없음** — `operation` 스키마가 이미 있다 |
| **`compose.yaml`** | **변경 없음** — master는 이미 `*depends-db`를 갖는다 (ems 01 함정 4를 후보에 올려 확인함) |
| `apps/master-service/CLAUDE.md` | 「`failOnNoDiscoveredTests`가 걸려 있다」 1문단 — 이번 변경이 거짓으로 만든 자리라 최소 수정 (리뷰 반영) |
| `docs/sync/주장-대조표.md` | 명제 A15를 ⚠️ → ✅ (리뷰 반영) |
| **`AbstractIntegrationTest`** | **변경 없음** — 이미 있고 `create-schemas=true`가 걸려 있다 |
| **ems·autonomous 모듈** | **변경 없음** — 이번 범위는 DDL뿐. write 주체 구현은 다음 단계 |
| **`infrastructure/docker/compose.yaml`** | **변경 없음** — master는 이미 `*depends-db`를 갖는다 |
| **`docs/architecture/…md` 11.3** | **변경 없음 (미결)** — 아래 「알려진 한계」 8번. 조정이 필요하나 이번 범위에서 결정하지 않았다 |
| **`docs/erd/스마트정수장.damx` · `.db`** | **이 작업이 변경하지 않았다. 단 작업트리에는 두 파일 모두 `M` 상태다** — DA# 쪽에서 병행 작업된 것이며 이 커밋에 딸려 들어갈 수 있다. 한계 1번·함정 기록 3번 |

## 구현 상세

### ERD 원문에서 정정한 것

ems 01의 「ERD 원문에서 정정한 6가지」와 같은 규칙을 적용하되, 이번은 실질 변경이 더해진다.

| 구분 | # | 정정 | 근거 |
|---|---|---|---|
| 형식 | 1 | 식별자를 소문자 무따옴표로 | V2 헤더 — PG가 소문자로 폴딩 |
| 형식 | 2 | `DROP TABLE IF EXISTS … CASCADE` 제거 | ems 01 정정 3과 동일 |
| 형식 | 3 | PK 인덱스명 `_pkey` → `_u_idx` | 2단계 PK 생성 시 PG가 제약 이름으로 자동 개명 |
| 형식 | 4 | FK 제약 없음 | V2 — 참조 정합성은 애플리케이션 책임 |
| **예외** | 5 | **스키마명을 하드코딩한다** (`operation.`) | 아래 별도 절 |
| 실질 | 6 | `mdf_*` 삭제 · 4개 컬럼 추가 | 결정 2 |

### 스키마명 하드코딩 — 이 파일만의 의도된 예외

V2·V1이 세운 규칙은 "스키마명을 하드코딩하지 않는다 — `default-schema`가 대상을 결정한다"이다.
**이 파일은 그 규칙을 깬다.** master의 `default-schema`는 `master`이므로 접두어 없이 적으면
`master.drvmd_chg_h`가 만들어진다. `schemas: master,operation`은 Flyway의 *관리 대상 목록*일 뿐
어느 스키마에 만들지를 정하지 않는다.

이 함정은 조용하다 — 마이그레이션은 성공하고, 테이블은 생기고, 다만 잘못된 스키마에 생긴다.
검증 1번이 그것만을 위해 존재한다.

### 컬럼 순서

업무컬럼 → `rgstr_*` 순. ems 01 정정 6번이 세운 규칙이고, `mdf_*`가 없으므로 거기서 끝난다.
업무컬럼 안에서는 **대상 → 전이 → 발행 → 사유** 순으로, 읽는 사람이 "무엇이 / 어떻게 바뀌었고 /
누가 / 왜"를 그 순서로 따라가게 둔다.

### JPA 기반 클래스

`rgstr_*`만 두므로 `BaseCreatedEntity`를 상속한다 — `master.eqp_tag_p`·`ems.ctrl_eqp_p`와 동형이다.
엔티티는 이번 범위에 없다(다음 단계 1).

## 함정 기록

1. **`spring.flyway.schemas`는 "만들 스키마 목록"이지 "DDL을 적용할 스키마"가 아니다.**
   `schemas: master,operation`이 배선돼 있어 접두어 없이 적어도 `operation`에 갈 것처럼 읽히지만,
   대상을 정하는 것은 `default-schema`(=`master`) 단독이다. 이 파일이 V2·ems V1과 반대로
   스키마명을 하드코딩하는 이유가 그것이다.

2. **Flyway 로그는 이 실패를 알려주지 않는다.** 실측 로그가
   `Migrating schema "master" to version "3 - operation drvmd chg"` 라고 말한다 —
   테이블이 실제로 `operation`에 만들어졌는데도 그렇다. Flyway가 말하는 "schema"는
   **히스토리 테이블을 두는 스키마**일 뿐 DDL의 적용 대상과 무관하다.
   따라서 스키마를 틀렸을 때도 로그는 **글자 하나 다르지 않다.**
   `테이블이_operation_스키마에_생성된다()`가 이 실패를 잡는 유일한 장치이고,
   그 테스트가 `master` 스키마에 없다는 것까지 함께 단언하는 이유다.

3. **`docs/erd/스마트정수장.db`를 조회할 때는 반드시 읽기 전용으로 연다.**
   이 파일은 ERD가 아니라 **DA# 표준단어사전**(SQLite, `STD_DIC` 등 14개 테이블)이고 SSOT의 일부다.
   조사 과정에서 `sqlite3.connect(path)`로 열었더니 SELECT만 했는데도 파일 크기와 mtime이 바뀌어
   `git status`에 수정으로 잡혔다. SQLite는 기본이 읽기/쓰기 모드라 연결만으로 페이지를 손댄다.
   HEAD와 14개 테이블 전부를 행 수·내용 해시로 대조해 **삭제·변조가 없음**을 확인했고,
   이후 조회는 `sqlite3.connect('file:...?mode=ro', uri=True)`로 바꿨다.
   **읽기만 할 작정이어도 도구가 그렇게 동작한다는 보장은 없다.**

   차이는 3개 테이블에서 **추가 14행**이었고 삭제는 0건이다 — 전부 DA# 쪽 작업이다.
   `STD_DIC` +6(`CTRL_TRGT_TYPE_CD`·`ISS_SVC_CD`·`RSN`·`SVC` 등),
   `STD_WORD_COMBI` +7(단어 조합), `STD_DIC_REL` +1(관계).
   뒤의 8행이 한계 1번의 "구성 단어 조합만으로 성립한다"는 판단의 직접 증거다.

4. **`BIGSERIAL`이 만드는 시퀀스도 테이블을 따라 `operation`에 놓인다.** 별도 지정이 필요할까
   확인했는데 불필요했다 — PG가 `<schema>.<table>_<column>_seq`로 같은 스키마에 만든다.
   master·ems의 기존 테이블이 전부 `VARCHAR` PK라 이 프로젝트에 선례가 없었다.

### 리뷰에서 나와 고친 것

5. **조회 인덱스에 판별자를 빠뜨렸다.** [/code-review 중간]
   결정 3에서 "`ctrl_trgt_id`는 다형 참조라 판별자 없이는 해석이 안 된다"고 논증해 놓고
   인덱스는 `(ctrl_trgt_id, rgstr_dttm DESC)`로 만들었다. **주장과 조회 경로가 어긋난 것**이다.
   `ctrl_trgt_id`는 애플리케이션이 부여하는 값이라(`prcs_m`·`ctrl_grp_m` 어디에도 생성 전략이 없다)
   공정과 제어그룹이 같은 ID를 가질 수 있고, 그러면 지배적 조회가 **두 대상의 이력을 섞어 반환**한다.
   판별자를 `WHERE`에만 넣어도 인덱스 선두가 아니면 필터로만 걸린다.
   `(ctrl_trgt_type_cd, ctrl_trgt_id, rgstr_dttm DESC)`로 고쳤다.
   **결정문에 쓴 근거가 스키마의 다른 부분까지 일관되게 적용됐는지는 따로 확인해야 한다.**

6. **시퀀스 스키마 단언이 `search_path`에 의존하고 있었다.** [/code-review 낮음]
   `column_default`의 `nextval('operation.…')` 표기는 `pg_get_expr`가 **현재 `search_path` 기준으로**
   렌더링한 결과다. `01-schemas.sql`이 이미 `ALTER DATABASE swtp SET search_path`를 쓰고 있어,
   거기에 `operation`이 추가되는 순간 접두가 사라지고 **스키마가 완벽히 옳은데도 테스트가 깨진다.**
   `pg_get_serial_sequence(...)`로 시퀀스의 소속 스키마를 직접 묻도록 바꿨다.
   문자열 렌더링을 단언하면 렌더링 규칙이 계약이 된다.

7. **`FK_제약이_없다()`에만 존재 앵커가 없었다.** [전용 리뷰]
   `contype='f'`가 0건인지만 봐서 **테이블이 아예 없어도 통과**하는 형태였다.
   같은 클래스의 `수정_감사_컬럼을_두지_않는다()`는 주석까지 달아 존재 단언을 앞세웠는데
   이 메서드만 빠져 있었다 — ems 01 함정 5가 "**한 테스트 클래스 안에서 단언 강도가 갈려 있었던 것**이
   놓친 이유"라고 적은 상황이 그대로 재현됐다. `contype`별 집계로 바꿔 `p:1`을 함께 단언한다.

8. **`countColumns`가 스키마 전체를 세고 있었다.** [/code-review 낮음]
   개수를 `COLUMNS.size()`(=11)와 대조하는데 대상이 `operation` 스키마 전체라,
   11.3이 예고한 `control_command`가 들어오는 날 **코멘트가 전부 멀쩡해도** 실패한다.
   테이블 필터를 넣었다. 다만 "코멘트 없는 컬럼 0건"은 스키마 전체를 그대로 두었다 —
   그쪽은 테이블이 늘어도 깨지지 않고 새 테이블의 코멘트 누락을 잡는 그물로 남는다.
   같은 이유로 `containsExactly(TABLE)`은 **완화하지 않고 의도를 주석으로 명시**했다(아래 함정 9의 기각 2번).

9. **`MasterMigrationIntegrationTest`라는 없는 클래스를 헤더가 가리키고 있었다.** [양쪽 리뷰]
   작성 도중 클래스명을 `OperationMigrationIntegrationTest`로 바꾸면서 SQL 헤더를 고치지 않았다.
   하필 그 문장이 **이 파일 최대 위험(스키마 접두)의 유일한 안전장치를 지목하는 자리**였다.
   메서드명까지 적어 정정했다.

10. **COMMENT 배치가 선례와 달랐다.** [전용 리뷰 낮음]
    V2·ems V1은 `CREATE TABLE → COMMENT 묶음 → 인덱스/ALTER` 순인데 V3은 인덱스를 먼저 뒀다.
    단일 테이블이라 실질 영향은 없으나 "ERD 재익스포트와 대조 가능하도록 원본 형태를 유지한다"는
    V2 헤더의 명분과 어긋나고, 두 번째 테이블이 들어오면 형이 갈린다. 선례 순서로 옮겼다.

11. **이번 커밋이 거짓으로 만든 문장을 갱신 후보에조차 올리지 않았다.** [전용 리뷰 높음]
    `apps/master-service/CLAUDE.md`가 "`failOnNoDiscoveredTests = false`가 걸려 있다"고 말하는데
    이번에 그 블록을 지웠고, `docs/sync/주장-대조표.md`의 명제 A15도 ⚠️인 채였다.
    **ems 01 함정 8의 정확한 재발**이며, 그 문서가 원인으로 지목한 "후보에조차 올리지 않음"까지 같다.
    변경 내역표에 "변경 없음"으로도 등장하지 않았다.
    교훈은 ems 01이 이미 적어 두었다 — **"이 서비스가 무엇을 새로 필요로 하게 됐는가"가 아니라
    "이 커밋이 어떤 문장을 거짓으로 만들었는가"를 물어야 한다.** 두 곳 모두 최소 수정했다.

### 리뷰 지적 중 기각한 것

12. **"작업트리 ERD에서 `MDF_DTTM`/`MDF_ID`가 삭제됐고, 따라서 「재확인함」이 거짓이며
    대조표의 4건은 3건이다"** [전용 리뷰, 높음 2건] — **사실이 아니어서 기각했다.**
    리뷰어의 근거는 `.damx` **파일 전역**의 `MDF_DTTM` 출현 횟수 감소(24→22)였는데,
    그 토큰은 마스터 테이블 전부가 갖고 있어 어느 엔티티에서 줄었는지 특정하지 못한다.
    HEAD와 작업트리 양쪽에서 `DRVMD_CHG_H` **근방 토큰**을 뽑아 대조한 결과
    `MDF_DTTM`·`MDF_ID`는 **양쪽 모두에 그대로 있다**. 따라서 실질 변경은 4건이 맞다.
    다만 같은 조사에서 리뷰어의 다른 지적(`ISS_SVC_CD`가 HEAD에 없다)은 **사실로 확인**돼
    아래 한계 9번에 남겼다. **바이너리 대조에서 전역 카운트는 엔티티 단위 결론의 근거가 되지 못한다.**

13. **`containsExactly(TABLE)`을 `contains`로 완화하라** [/code-review 낮음] — **기각했다.**
    두 리뷰가 갈린 지점이다. 전용 리뷰는 같은 코드를 "지금은 새 테이블을 잡는 그물이라 장점"으로 봤다.
    `operation`은 여러 서비스가 write 하는 공용 스키마라 무엇이 사는지를 좁게 못박아 두는 값이 크고,
    `control_command`가 들어올 때 그 마이그레이션이 자기 검증과 함께 이 목록을 갱신하는 것이 정상 작업이다.
    완화하면 그물이 사라진다. 대신 그 의도를 코드 주석으로 남겨 다음 사람이 무심코 풀지 않게 했다.

## 알려진 한계

1. **ERD 엔티티가 아직 이 파일을 따라오지 못했다.** 결정 2의 실질 변경 4건이 `.damx`에 없다
   (`MDF_DTTM`·`MDF_ID`는 그대로 있고 추가 4개 컬럼은 없음 — 작업 종료 시점에 재확인함).
   DA# 전용 바이너리라 이 세션에서 편집할 수 없었다. **DA#에서 아래를 반영하고 재익스포트해야
   SSOT가 복구된다** — 그 전까지 ERD와 코드는 갈라져 있다.

   **선행 절차인 표준단어 등록은 이미 되어 있다.** 작업 중 `docs/erd/스마트정수장.db`에
   `CTRL_TRGT_TYPE_CD`(제어대상유형코드)·`ISS_SVC_CD`(발행서비스코드)·`RSN`(사유)·`SVC`(서비스)가
   추가된 것을 확인했다. 즉 남은 것은 엔티티에 컬럼을 붙이는 일뿐이다.
   `CTRL_TRGT_NM`·`CHG_RSN_CD`·`CHG_RSN_RMRK`는 구성 단어(`NM`·`CHG`·`RSN`·`RMRK`)가
   모두 이미 표준어라 신규 등록 없이 조합만으로 성립한다.

   | 대상 | 작업 |
   |---|---|
   | `MDF_DTTM` · `MDF_ID` | 삭제 |
   | `CTRL_TRGT_TYPE_CD` VARCHAR(10) NOT NULL '제어대상유형코드' | 추가 (`CTRL_TRGT_ID` 앞) |
   | `CTRL_TRGT_NM` VARCHAR(50) NOT NULL '제어대상명' | 추가 (`CTRL_TRGT_ID` 뒤) |
   | `CHG_RSN_CD` VARCHAR(10) NOT NULL '변경사유코드' | 추가 (`ISS_SVC_CD` 뒤) |
   | `CHG_RSN_RMRK` VARCHAR(500) NULL '변경사유비고' | 추가 |

2. **`CHG`는 표준단어사전상 "교체"이고 "변경"은 `CHNG`다.** 즉 `DRVMD_CHG_H`와 `CHG_RSN_CD`는
   엄밀히는 사전 위반이다. ERD 원본이 `CHG`를 쓰고 있어 그대로 따랐다 — 여기서 `CHNG`로 바꾸면
   ERD와 코드가 **한 겹 더** 갈라진다. 한계 1번의 재익스포트 때 함께 판단할 사안이며,
   바꾼다면 테이블명까지 바뀌므로 마이그레이션 신설이 아니라 ERD 쪽 결정이 먼저다.

3. **➖ 검증불가 — 운영 경로(`create-schemas: false`).** 실측 로그가 `Creating schema "operation"`을
   찍는다. Testcontainers에는 init SQL이 없어 Flyway가 직접 만든 것이다.
   운영은 반대로 `01-schemas.sql`이 만든 `operation`에 `create-schemas: false`로 붙는다.
   **그 경로는 이번 검증이 재현하지 못한다.** 다만 `operation` 스키마는 그 파일이 이미 만들고 있고
   (`-- operation: 제어 명령 이력 + 운전모드`), 같은 구조인 job-service의 3스키마 소유가 동작 중이다.

4. **master-service의 사라진 `V1__sample_item.sql`이 이 마이그레이션의 반입을 막을 수 있다.**
   ems 01의 「다음 단계」 2번이 지목한 별건이다 — 개발 DB에 V1이 적용된 상태라면 Flyway `validate`가
   missing migration으로 기동을 세운다. **V3을 추가한다고 나빠지지는 않으나, 이 파일을 개발 DB에
   반영하려면 그 건이 먼저 해결돼야 한다**(`docker compose down -v` 또는 `ignore-migration-patterns`).
   이번 범위 밖이며 별도 `/step` 대상이다.

5. **"write는 각 서비스"라는 계약을 강제하는 장치가 없다.** 마이그레이션 헤더와 이 문서, 그리고
   `apps/autonomous-service/CLAUDE.md`·`apps/master-service/CLAUDE.md`의 서술이 전부다.
   master에 이력 write 서비스가 실수로 생기는 것을 빌드가 막지 않는다.
   엔티티를 붙이는 다음 단계에서 판단할 사안이다 — 예컨대 master 쪽에는 조회 전용 인터페이스만 두는 식이다.
   → **해소됨**: `04-운전모드-변경이력-조회.md`의 결정 3. master에 `@Entity`도 `JpaRepository`도 두지 않아
   `save()`가 존재하지 않는다. 다만 강제 강도는 "빌드 실패"가 아니라 "구조적 부재"다(04의 알려진 한계 2).

6. **코드값 5종의 관리 주체가 정해지지 않았다.** `ctrl_trgt_type_cd`·`bf/af_drvmd_cd`·`iss_svc_cd`·
   `chg_rsn_cd`가 모두 문자열이고 CHECK도 공통코드 테이블도 없다. 기존 `prcs_type_cd` 등도
   같은 상태라 이 파일만의 문제는 아니나, **이력 데이터는 잘못 쌓이면 소급 정정이 불가능**해
   일반 마스터보다 위험이 크다. 다음 단계 3번.
   → **해소됨**: `03-운전모드-사유-코드체계.md`가 `libs/swtp-common`에 enum 4종으로 정의했다.
   다만 DB가 값을 강제하지 않는 상태는 그대로다(03의 알려진 한계 2번).

7. **`bf_drvmd_cd`의 최초 값이 정해지지 않았다.** NOT NULL이므로 어떤 대상의 첫 전환 이력에도
   "이전 모드"가 필요하다. 초기 코드(`INIT` 등)를 코드 체계에 넣거나, 대상 등록 시점에
   초기 모드 이력을 한 행 넣는 방식 중 하나를 택해야 한다. 6번과 함께 결정한다.
   → **해소됨**: `03`의 결정 5번. 기본값이 `AI_ANLS`(AI 미개입)로 확정되면서 **특수값이 불필요해졌다** —
   이력이 없다 = 한 번도 안 바뀜 = `AI_ANLS`이고, 첫 전환 행의 `bf`에 그 값을 넣는다.
   위 두 대안은 모두 기각됐다.

8. **아키텍처 11.3이 예고한 `operation.operation_mode`와의 관계를 정리하지 않았다.** [전용 리뷰]
   11.3은 `operation.operation_mode -- 공정별 운전모드 (자동/수동/원격)`를 적어 두었는데,
   이번에 만든 것은 이름도 성격도 다르다 — **현재 상태 1행**이 아니라 **다형 축의 append-only 이력**이다.
   둘은 대체 관계가 아니라 보완 관계로 보는 것이 맞다고 판단하나(모드 판정은 제어 인터록의
   hot path라 단일행 조회가 빨라야 하고, 이력에서 `MAX(rgstr_dttm)`로 뽑으면 그 경로가 느려진다),
   **이번 범위에서 결정하지 않았고 아키텍처 문서도 고치지 않았다.**
   다음 단계 2·3에서 `operation_mode`를 별도로 둘지와 함께 결정하고, 그때 11.3을 조정한다.
   그전까지 아키텍처 문서와 실물은 어긋나 있다.
   → **결정됨**: `04-운전모드-변경이력-조회.md`의 결정 4 — **만들지 않는다.**
   이력이 SSOT이고 현재 모드는 대상별 최신 1행의 `af_drvmd_cd`, 행이 없으면 `AI_ANLS`다.
   "인터록 hot path"라는 근거는 제어 경로가 아직 없어 실측 대상이 없고, 갱신 주체(ems·autonomous)가
   없는 상태에서 테이블만 만들면 아무도 채우지 않는 테이블이 먼저 생긴다.
   **11.3과의 불일치는 해소되지 않고 이월된다**(04의 알려진 한계 6).

9. **`iss_svc_cd`는 커밋된 ERD에는 없다.** [전용 리뷰]
   작업 중 `.damx`에 추가된 컬럼이며(HEAD 대비 대조로 확인), 표준단어 `ISS_SVC_CD`도
   같은 시각 `STD_DIC`에 등재됐다. 즉 이 컬럼은 결정 2의 "실질 변경 4건"에는 들어가지 않지만
   **커밋 이력만 보는 사람에게는 출처가 불명해진다** — ERD 변경이 아직 커밋되지 않았기 때문이다.
   한계 1의 재익스포트·커밋이 이루어지면 함께 해소된다.

10. **`operation`에 첫 테이블이 생기면서 개발용 스키마 리셋 절차가 깨진다.** [/code-review]
    `spring.flyway.schemas: master,operation`이라 Flyway는 히스토리 테이블이 없을 때
    **설정된 모든 스키마**를 검사하고, 비어 있지 않으면
    `Found non-empty schema(s) … but no schema history table`로 기동을 세운다.
    지금까지 `operation`이 늘 비어 있어서 "`master`만 드롭하고 재기동"이 통했는데,
    V3 적용 후에는 `operation.drvmd_chg_h`가 살아남아 master-service가 뜨지 않는다.
    **두 스키마를 함께 리셋해야 한다**(`docker compose down -v` 또는 두 스키마 동시 drop).
    `baselineOnMigrate`를 켜지 않는다 — 운영에서 히스토리 없이 baseline이 찍히면
    적용되지 않은 마이그레이션을 적용된 것으로 오인한다. 한계 4번과 함께 밟는 문제다.

## 검증

```bash
./gradlew :apps:master-service:build
```

Testcontainers PG(`timescale/timescaledb-ha:pg17`)에서 V2·V3이 적용되고 다음이 참인지 확인한다.

1. `drvmd_chg_h`가 **`operation`** 스키마에 있다 — `master`에는 없다 (스키마 접두 검증, 이번 최대 위험)
2. PK가 `hist_id` 단독이고, 그 시퀀스가 `operation` 스키마에 놓인다 (문자열 렌더링이 아니라 소속을 직접 조회)
3. `mdf_dttm`/`mdf_id`가 **없다** — 다른 컬럼의 존재 단언을 함께 두어 공허 참을 막는다 (ems 01 함정 5)
4. `rgstr_dttm`/`rgstr_id`가 NOT NULL
5. 한글 코멘트 — 한글 포함 컬럼 수 = 전체 컬럼 수 = 11 (ems 01 함정 6, 부정형 단언 금지)
6. FK 제약 0건 **且 PK 1건** — 존재 앵커를 같은 쿼리에 얹는다 (리뷰 반영, 함정 7)
7. 조회 인덱스가 `(ctrl_trgt_type_cd, ctrl_trgt_id, rgstr_dttm DESC)`다 — **컬럼 순서까지** 대조한다
8. `flyway_schema_history`가 `master` 단독 — `operation`에는 생기지 않는다
9. 테스트가 실제로 discovered 된다 (`tests="N"`)

### 실측 결과 (2026-08-24, 리뷰 반영 후 재실행)

```
> Task :apps:master-service:verifyIntegrationTestBaseline
> Task :apps:master-service:test
> Task :apps:master-service:check
> Task :apps:master-service:build

BUILD SUCCESSFUL
```

리뷰 반영 과정에서 **한 번 빨갛게 만들었다가 고쳤다.** FK 테스트에 존재 앵커를 얹으면서
`c.contype || ':' || count(*)`로 썼는데 `contype`이 `"char"` 타입이라 `||`가 없어
`BadSqlGrammarException`으로 `10 tests completed, 1 failed`가 났다. `::text` 캐스팅으로 고쳤다.
**단언을 강화하는 변경도 회귀를 만든다** — 강화 후 재실행이 필수다.

Flyway 적용 로그 — Testcontainers PG에서 뽑은 원문이다. 읽기 좋게 재배열하지 않는다.

```
Database: jdbc:postgresql://localhost:11571/test?loggerLevel=OFF (PostgreSQL 17.10)
Creating schema "master" ...
Creating schema "operation" ...
Current version of schema "master": null
Migrating schema "master" to version "2 - master domain"
will rename index "tag_m_u_idx" to "tag_m_pkey"
will rename index "prcs_m_u_idx" to "prcs_m_pkey"
will rename index "fclt_m_u_idx" to "fclt_m_pkey"
will rename index "prcs_fclt_r_u_idx01" to "prcs_fclt_r_pkey"
will rename index "eqp_m_u_idx" to "eqp_m_pkey"
will rename index "eqp_tag_p_u_idx" to "eqp_tag_p_pkey"
Migrating schema "master" to version "3 - operation drvmd chg"
will rename index "drvmd_chg_h_u_idx" to "drvmd_chg_h_pkey"
Successfully applied 2 migrations to schema "master", now at version v3 (execution time 00:00.132s)
```

**이 로그는 검증 1번의 증거가 되지 못한다.** `Migrating schema "master" to version "3"`이라고
적혀 있지만 테이블은 `operation`에 만들어졌다 — Flyway가 말하는 "schema"는 히스토리 위치일 뿐이다.
스키마를 틀렸더라도 이 로그는 **동일하게 출력된다**(함정 기록 2). 증거는 테스트 쪽에만 있다.
`will rename index "drvmd_chg_h_u_idx" to "drvmd_chg_h_pkey"` 한 줄만이 2단계 PK 생성의 직접 증거다.

테스트 결과 — `build/test-results/test/TEST-com.mo.swtp.master.OperationMigrationIntegrationTest.xml`:

```
tests="10" skipped="0" failures="0" errors="0"
```

| 검증 항목 | 테스트 메서드 | 실제 단언하는 것 | 결과 |
|---|---|---|---|
| 1. `operation` 스키마 생성 | `테이블이_operation_스키마에_생성된다` | `operation`의 테이블이 정확히 `drvmd_chg_h` 하나 **且** `master`에 같은 이름이 없음 | ✅ |
| 2. PK·시퀀스 소속 | `PK가_hist_id_단독이다` | `pg_constraint` contype='p'가 `hist_id` 단독 + `data_type`=bigint + `pg_get_serial_sequence`가 가리키는 시퀀스의 `nspname`='operation' | ✅ |
| 2'. `_u_idx` → `_pkey` | `PK_인덱스가_제약_이름으로_개명된다` | `_pkey` 포함 **且** `_u_idx` 미포함 | ✅ |
| 3. `mdf_*` 부재 | `수정_감사_컬럼을_두지_않는다` | `rgstr_*` **존재**를 먼저 단언한 뒤 `mdf_*` 부재 (공허 참 차단) | ✅ |
| 4. NULL 허용 범위 | `비고만_NULL을_허용한다` | nullable 컬럼이 정확히 `chg_rsn_rmrk` 하나 — `chg_rsn_cd`가 NOT NULL임이 여기서 증명된다 | ✅ |
| 4'. 컬럼 구성·순서 | `컬럼_구성이_결정대로다` | `ordinal_position` 순 11개가 결정과 정확히 일치 | ✅ |
| 5. 한글 코멘트 | `한글_코멘트가_빠짐없이_달려_있다` | 코멘트 없는 것 0건 + **한글 포함 컬럼 수 = 전체 컬럼 수 = 11** | ✅ |
| 6. FK 없음 | `FK_제약이_없다` | contype별 집계가 정확히 `p:1` — FK 0건과 PK 1건을 한 쿼리로 (테이블 부재 시 통과하는 형태가 아님) | ✅ |
| 7. 조회 인덱스 | `조회_인덱스가_시간_역순이다` | `indexdef`에 `(ctrl_trgt_type_cd, ctrl_trgt_id, rgstr_dttm DESC)` **문자열 그대로** 포함(=컬럼 순서 확정), `UNIQUE` 미포함 | ✅ |
| 8. 히스토리 위치 | `히스토리_테이블이_master에만_있다` | `flyway_schema_history`가 `master` 단독 — `operation`에 없음 | ✅ |
| 9. 테스트가 실제로 돌았나 | XML `tests="10"` | `failOnNoDiscoveredTests` 가드 제거 후 10건 discovered | ✅ |

**"실제 단언하는 것" 열을 따로 두는 이유**는 ems 01의 함정 5·6이 보여준 바와 같다 —
`✅`와 `failures="0"`은 그 단언이 무엇을 증명하는지 말해주지 않는다.
항목 3과 5는 그 함정을 처음부터 피하도록 설계했다.

## 다음 단계

1. **ERD 재익스포트** — 알려진 한계 1번. 이 작업이 남아 있는 동안 SSOT는 코드 쪽에 있다.
   가장 먼저 해소되어야 할 부채다.

2. **코드 체계 정의** — 알려진 한계 6·7번. `ctrl_trgt_type_cd`·`drvmd_cd`·`iss_svc_cd`·`chg_rsn_cd`
   4종의 값 집합과 관리 주체(공통코드 테이블 신설 여부, 초기 모드 코드)를 정한다.
   **엔티티보다 먼저**다 — 이력은 잘못 쌓이면 소급 정정이 불가능하다.

3. **JPA 엔티티 + master 통합 조회 API** — `BaseCreatedEntity` 상속.
   조회 API는 `ctrl_trgt_nm` 스냅샷 덕에 ems 스키마를 읽지 않아도 된다(결정 4).
   이때 알려진 한계 5번(write 계약 강제)을 함께 결정한다.

4. **각 서비스의 write 경로** — ems·autonomous가 모드 전환 트랜잭션에서 직접 INSERT.
   `iss_svc_cd`를 서비스가 자기 코드로 박는 것이 계약의 핵심이다(결정 1).

5. **`operation.control_command`** — 아키텍처 11.3이 예고한 나머지 절반이다.
   운전모드 전환과 달리 명령/결과의 시점이 갈리므로 단일 행 UPDATE인지 2행 append인지를
   먼저 정해야 한다. 이 테이블과 조회 화면을 공유하므로 컬럼 체계를 맞춰 설계한다.

6. **master `V1__sample_item.sql` missing 해소** — 알려진 한계 4번. 별도 `/step` 대상.
