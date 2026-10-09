# 02 — 제어그룹·수계통지점 마스터 CRUD와 도메인 슬라이스 신설

- 일자: 2026-08-25
- 상태: ✅ 완료 (`:apps:ems-service:build` 통과, 통합 테스트 31/31. 컨테이너 E2E 미실행 — 아래 「알려진 한계」 2)
- 리뷰: /code-review ✅ (8건 중 5건 반영, 3건은 「알려진 한계」 6·7·8로) · 전용 ✅ (9건 전부 반영)
- 검토 관점: 정수장 도메인 · 데이터 · 분산 아키텍처
- 관련: [01](01-제어그룹-도메인-DDL.md)(DDL과 「다음 단계 1」), `apps/master-service/docs/01-패키지-규약.md`(A형 기준형), `apps/CLAUDE.md`「패키지 규약」·「통합 테스트 표준형」

## 발단

[01](01-제어그룹-도메인-DDL.md)이 「다음 단계 1」에서 이 작업을 지목하면서 **차단 조건 하나를 함께 걸어 뒀다** —
*"복제 대상인 master-service 도메인 슬라이스가 지금 패키지 리팩터링 중이므로 그것이 끝난 뒤가 낫다."*

`apps/master-service/docs/01-패키지-규약.md`가 ✅ 완료이고 `master/{tag,prcs,eqp,fclt}/{web,service,repository,dto,domain}`
A형 4슬라이스가 정착했다. **차단 조건이 해제됐다.**

현재 ems는 Flyway가 만든 테이블 5종과 그것을 읽는 자바 코드 사이가 통째로 비어 있다.
`EmsServiceApplication` 한 개 + 마이그레이션 검증 테스트가 전부다.

`AbstractIntegrationTest`가 자기 주석에 다음 커밋의 할 일을 미리 적어 뒀다 —
*"REST 표면이 아직 없어 TestRestTemplate 배선도 두지 않는다. 컨트롤러가 들어오는 커밋이
`@AutoConfigureTestRestTemplate`와 `spring-boot-resttestclient` 의존을 함께 넣는다."* 그 커밋이 이번 것이다.

---

## 결정 (2026-08-25)

### 1. 범위를 마스터 2종 CRUD로 한정한다 — 편성 API는 03으로 미룬다

제어그룹 도메인 전체(마스터 2종 + 편성 3종)를 한 커밋에 넣지 않는다.
**미증명 위험 3개가 편성 API의 전제이기 때문**이고, 그것을 먼저 실증하는 순서를 택한다.

| 미증명 항목 | 왜 미증명인가 | 어디서 증명하나 |
|---|---|---|
| `CHARACTER(1)` + `@Enumerated(STRING)` 왕복 | master의 `Tag`·`Prcs`가 같은 매핑이나 **그 엔티티를 실 DB에 저장·조회하는 테스트가 리포에 0건**이다. auth에는 `@Enumerated`가 0건 | **02** |
| assigned-ID에서 `save()`가 `merge()`를 타는 문제 | auth의 `RefreshToken`은 `@GeneratedValue(IDENTITY)`라 겪은 적이 없다. `Persistable`·`@Version`도 리포 전체에 0건 | **02** |
| 같은 트랜잭션 delete-then-insert의 flush 순서 | 편성 API가 없어 발생한 적이 없다 | 03 |

앞의 둘이 02에서 초록이 되면 03이 그 위에 선다. 한 커밋에 뭉치면 편성 구현 중 터진 오류의 원인이
편성 로직인지 매핑 자체인지 갈라내야 한다.

**감사 경로(`BaseEntity`)는 이번에 증명할 대상이 아니다.** `AuthSliceIntegrationTest.auditColumnsFollowTheConvention()`이
HTTP를 통해 JPA 엔티티를 실제 PG에 넣고 감사 4컬럼을 SQL로 되읽어 이미 증명해 뒀다.
다만 그 테스트의 감사 주체는 `SYSTEM`이다(로그인은 비인증 경로) — **토큰 `sub`가 `rgstr_id`에 박히는 경로는
아직 아무도 증명하지 않았다.** ems는 전 엔드포인트가 인증 대상이라 그 경로를 처음 밟는다.
`master-service/docs/01` 「한계 3」이 남긴 *"그 도메인 API가 생길 때 감사 컬럼이 실제로 채워지는지 확인해야 한다"* 가
여기서 해소된다.

### 2. A형 2단 — `ctrl`과 `wnp` 두 도메인이다

`apps/CLAUDE.md`의 판정 기준은 *"독립적인 CRUD API 표면과 자기 테이블을 갖는 소유권 단위"* 다.

| 후보 | 자기 테이블 | 독립 CRUD 표면 | 판정 |
|---|---|---|---|
| `ctrl` | `ctrl_grp_m` + 편성 3종(03) | 제어그룹 등록·수정·조회 | ✅ |
| `wnp` | `wnp_m` | 제어그룹을 만들기 전에 지점 목록이 먼저 있어야 편성이 가능하다 | ✅ |

정수장 도메인 관점과 분산 아키텍처 관점이 **서로 다른 근거에서 같은 답**에 왔다.
도메인 관점은 변경 주기 — 수계통지점은 배관·분기점 구조라 공사 없이는 안 바뀌고, 제어그룹은
"송수펌프 몇 호기를 한 묶음으로 돌릴 것인가"라는 운전 편성이라 계절·수요·정비에 따라 재편성된다.
**변경 주기가 다르면 화면이 갈린다.**

**기각한 대안 — B형(1단).** *"수계통지점은 EMS 제어 토폴로지의 일부"* (`V1__ems_domain.sql` 주석)라
auth의 `token`과 같은 지위라는 논리가 가능하다. 기각한다 — `apps/CLAUDE.md`가 token을 도메인으로
치지 않은 이유는 *"자기 REST 표면이 없고"* 인데, `wnp_m`은 `ctrl_grp_m`과 컬럼 구조가 완전히 동일한
독립 마스터 테이블(id/nm/use_yn/sort_ord + 감사 4)이다. **`wnp`가 지금 얇다는 것은 사실이지만 기준은 두께가 아니다.**

오판정의 대가가 비대칭인 것도 근거다. A형 오판(실제 1개인데 2단)은 껍데기 디렉토리가 남을 뿐이고
되돌리기가 import 경로 변경으로 끝난다. B형 오판(실제 2개인데 1단)은 같은 패키지라
`WnpService`가 `CtrlGrpRepository`를 직접 잡는 위반이 자라도 컴파일러가 막지 않는다 —
`apps/CLAUDE.md`가 *"이 방향들을 검사하는 자동 장치는 없다(ArchUnit 미도입). 리뷰가 유일한 그물이다"* 라고
스스로 인정한 상태다. **B형 오판은 조용히 굳는다.**

### 3. API 경로는 `/api/ems/ctrl-grp` · `/api/ems/wnp`

master가 `eqp`·`tag`·`prcs`·`fclt` 전부 단일 세그먼트라 **복합어 도메인의 선례가 리포에 없다.**
여기서 정하는 형태가 두 번째 복합어 도메인의 기준이 되므로 지금 못박는다 —
**언더스코어를 하이픈으로 바꾸고 테이블명과 1:1로 대응시킨다**(`ctrl_grp_m` → `ctrl-grp`).

**기각한 대안 — `/api/ems/ctrl`.** 짧고 패키지명과 일치하지만 테이블명과 한 글자 어긋나고,
하이픈 규약을 다음 도메인으로 미룬다. step-18이 *"규약이 둘이면 어느 쪽인지는 라우트 정의를 봐야만 안다"* 며
경로 규약의 이탈 비용을 이미 치른 자리다.

접두사 `/ems-service`를 컨트롤러에 붙이지 않는다 — 게이트웨이의 `RewritePath`가 벗기고,
step-18이 *"바뀐 것은 게이트웨이 외부 접두사뿐"* 이라고 못박았다.

| 구분 | 경로 |
|---|---|
| 컨트롤러 내부 | `/api/ems/ctrl-grp` · `/api/ems/wnp` |
| 게이트웨이 경유(프론트에 주는 값) | `/ems-service/api/ems/ctrl-grp` · `/ems-service/api/ems/wnp` |

### 4. 목록 조회가 비어 있으면 404가 아니라 빈 배열 200이다 — master와 갈라진다

master의 `TagService.getTagList()`·`PrcsService.getPrcsList()`는 결과가 비면
`BusinessException(CommonErrorCode.NOT_FOUND)`를 던진다. **ems는 그렇게 하지 않는다.**

빈 목록은 오류가 아니라 상태다. 신규 정수장은 제어그룹이 0건인 채로 출발하므로,
관리 화면을 처음 여는 순간 404가 떨어진다. 프론트는 *"아직 등록된 제어그룹이 없음"* 과
*"조회가 실패함"* 을 구분할 수 없고, 구분하려면 코드가 아니라 화면마다 예외를 외워야 한다.
master가 이 결함을 아직 만나지 않은 것은 **마스터 데이터가 이미 적재돼 있기 때문**이지
그 방식이 옳아서가 아니다.

단건 조회(`/{id}`)는 404를 유지한다 — 지목한 자원이 없는 것은 실제로 오류다.

> master 쪽 정정은 이번 범위 밖이다. 아래 「다음 단계」에 남긴다.

### 5. `EmsErrorCode`를 만들되 실제로 쓰이는 코드만 넣는다

`apps/CLAUDE.md`가 *"`<Svc>ErrorCode` — 앱당 enum 하나. 코드 문자열이 프론트 i18n 키라
유일성 보장 단위가 서비스다"* 라고 정했다. ems는 이 enum이 아직 없다.

이번에 넣는 것은 **`DUPLICATE_ID("EMS-409", 409)` 하나**다. 404는 `CommonErrorCode.NOT_FOUND`를
그대로 쓴다(master와 같다).

`EMS-503`(연동 실패) 같은 코드를 미리 넣지 않는다 — **코드 문자열이 프론트 i18n 키라
쓰이지 않는 키가 먼저 배포되면 지우기가 어려워진다.** HTTP 클라이언트가 실제로 들어올 때 함께 넣는다.

**`DUPLICATE_ID`가 장식이 아닌 이유**가 결정 1의 미증명 항목 ②와 직접 이어진다.
`ctrl_grp_id`가 애플리케이션이 넣는 assigned-ID이고 `Persistable` 구현이 없으므로,
Spring Data `SimpleJpaRepository.save()`의 `isNew()`가 항상 false다 → `em.merge()`를 탄다.
**즉 이미 있는 ID로 등록을 호출하면 예외가 아니라 조용한 UPDATE가 된다.**
`rgstr_dttm`은 `updatable = false`(`BaseCreatedEntity`)라 그대로 남고, 등록자만 바뀐 유령 행이 생긴다.
그래서 등록 경로에 사전 존재 검사를 두고 409로 끊는다(단건·일괄이 같은 경로를 타도록 `findAllById`를 쓴다).

> master의 `addTag`·`addPrcs`·`addEqp`·`addFclt`가 같은 잠복 결함을 갖고 있다. 별건이다.

### 6. 응답에 `mdf_*`를 싣는다 — master보다 두 필드 넓다

master의 `TagResponse`·`PrcsResponse`는 `rgstrDttm`·`rgstrId`만 담는다.
ems는 `mdfDttm`·`mdfId`도 담는다. 감사 컬럼의 목적이 추적인데 UI가 그것을 볼 경로가 응답뿐이고,
`BaseEntity`를 상속한 이상 **"마지막으로 누가 언제 바꿨나"가 이 도메인의 1급 정보**다.
제어그룹 편성은 운전에 직결되므로 특히 그렇다.

이것은 필드 추가일 뿐 형태 변경이 아니라 master와의 계약 충돌이 없다.

### 7. `support`는 master를 복제하되 상수를 이름으로 분리한다

`ColLength`·`UseYn`을 ems에 **따로 만든다.** 재사용은 불가능하다 —
루트 불변식 1(앱 모듈 간 `project()` 의존 금지)이 빌드를 실패시키고,
`libs/swtp-common`으로 올리는 것도 안 된다(컬럼 길이는 각 서비스가 소유한 스키마의 성질이지 공용 계약이 아니다).

**다만 master의 `ColLength`를 그대로 베끼지 않는다.** 현재 형태에 두 문제가 있다 —
(i) `PrcsFclt.java`가 `length = 36` 리터럴을 써서 상수화 규약이 이미 새고 있고,
(ii) `EQP_ID`/`PRCS_ID`/`FCLT_ID`가 전부 36이라 **상수를 잘못 골라도 아무도 모른다.**
ems에서는 값이 같아도 이름으로 분리하고 리터럴을 한 곳도 남기지 않는다.

### 8. 정렬은 `NULLS LAST` + 결정적 타이브레이커를 명시한다

`sort_ord`가 nullable이고(`V1__ems_domain.sql`) `(ctrl_grp_id, sort_ord)` 유니크도 없다.
같은 값·NULL이 공존할 수 있으므로 **정렬이 결정적이지 않다** — seq scan 순서는 보장되지 않아
같은 요청이 매번 다른 순서를 반환할 수 있다.

파생 쿼리 메서드로는 `NULLS LAST`를 표현할 수 없으므로 `@Query`로 HQL을 적는다.

```sql
order by c.sortOrd asc nulls last, c.ctrlGrpId asc
```

### 9. 검증 애노테이션을 넣되, 존재 검증이 아님을 명시한다

ems는 이미 `spring-boot-starter-validation`을 의존한다(01에서 추가). `@NotBlank`·`@Size`를 요청 record에 건다.
master는 검증 애노테이션이 0건이라 이것도 갈라지는 지점이다.

**형식 검증을 존재 검증으로 착각하지 않도록 주석에 그 차이를 적는다.**
`ctrl_eqp_p.eqp_id`·`ctrl_grp_tag_p.tag_sn`이 master 소유 키의 복제본인데 그 존재를 확인하는 장치는
여전히 없다(01 「한계 4」). 그건 03도 아닌 별도 작업이다 — 아래 「알려진 한계」에 승계한다.

---

## 이번 범위에서 뺀 것

세 관점이 각각 지목한 것을 사유와 함께 남긴다. 사유 없이 뺀 것은 없다.

| 뺀 것 | 사유 | 누가 |
|---|---|---|
| 편성 3종 replace-all API | 결정 1 — 미증명 전제를 먼저 실증한다 | (범위 분할) |
| ems→master 동기 HTTP 클라이언트 | **인증 방식이 미결이다.** master는 `anyRequest().authenticated()` 아래인데, 아키텍처 4.1이 *"다른 서비스가 auth-service를 직접 호출하지 않으므로 이 앱만 통째로 빠질 수 있다"* 며 서비스 계정 토큰을 원천 차단했다. 무모순 방법은 사용자 토큰 pass-through 하나뿐이고, 그 규약은 CRUD 커밋의 곁다리가 아니다 | 분산 |
| Kafka 이벤트 발행 | 아키텍처 9.3 초기 토픽 8종에 `ems.*`가 없고, 소비자 후보를 실측하면 현재 0이다(autonomous는 스캐폴드에 kafka 스타터도 없고, 11.3이 *"autonomous는 송수를 제외한 공정"* 으로 축을 갈라 제어그룹을 관심사에서 뺐다). **master조차 아직 발행자가 아니다** | 분산 · 도메인 |
| V2 마이그레이션 일체(인덱스·`mdf_*` 추가) | `V1__ems_domain.sql`이 *"ERD를 재익스포트해 대조할 수 있도록"* 원본 유지를 선언하고 변경 절차를 *"ERD를 먼저 고치고 재익스포트한다"* 로 정해 뒀다. **엔티티를 붙이는 커밋에 DDL 변경을 섞으면 변경 근거가 "구현이 불편해서"가 되어 SSOT가 역전된다** | 데이터 |
| `@ManyToOne`/`@OneToMany` | `apps/CLAUDE.md` *"도메인 간 타입 직접 참조 금지 … `@ManyToOne`을 하나 들이면 되돌리는 것이 스키마 논쟁이 된다"*, `V1__ems_domain.sql` *"FK 제약은 걸지 않는다"* | 데이터 · 분산 |
| 물리 DELETE 엔드포인트 | `use_yn='N'` 소프트 삭제만. master 컨트롤러 4종에 `@DeleteMapping`이 0개인 확립된 선례이고, 11.3이 `operation.control_command`를 *"사고 조사용 감사 로그"* 로 규정해 **이력이 참조하는 축은 지우지 않는다** | 도메인 |
| `sort_ord` 재정렬 전용 엔드포인트 | `V1__ems_domain.sql`이 *"sort_ord 재정렬을 UPDATE 로 하려면 mdf_* 를 추가해야 한다"* 고 적고 `ctrl_eqp_p`는 그렇게 하지 않았다 — 재정렬 UPDATE를 안 한다는 선언이다. 03의 replace-all이 이 문제를 소멸시킨다 | 도메인 · 데이터 |
| 수계통 토폴로지(지점 간 상하류·흐름방향) | `wnp_m`에 상위지점ID·연결·방향 컬럼이 하나도 없다. **스키마에 근거 없는 것을 코드로 먼저 만들면 반드시 틀린다** | 도메인 |
| 제어그룹 계층(1계열-2계열 상하위) | `ctrl_grp_m`에 상위그룹ID가 없다. 위와 같은 사유 | 도메인 |
| 제어 실행·명령 API | 아키텍처 4.3의 EMS 역할 6항목에 "제어 명령 생성"이 **없다**(4.2 Autonomous의 역할이다). `operation.control_command`의 `issuer_service`·`command_type`·`reason` 계약과 함께 설계할 일이고, 그 스키마의 DDL은 master 소유인데 `V3__operation_drvmd_chg.sql`이 지금 작업 중이다 — **움직이는 스키마에 붙지 않는다** | 도메인 · 분산 |
| `query/` · `event/` 패키지 | `apps/CLAUDE.md` "예약어 — 필요해질 때 만든다" | 분산 |
| `Persistable` 도입을 스타터로 올리기 | 사례가 ems 3개뿐이고 master의 `EqpTag`·`PrcsFclt`는 리포지토리조차 없어 같은 문제를 겪은 적이 없다 | 데이터 |
| `ddl-auto: validate` 전환 | bpchar↔VARCHAR 타입 불일치가 실재하지만 전환은 master 엔티티 6종까지 함께 검증해야 하는 별건이다. **ems 커밋이 master 수정 커밋이 된다** | 데이터 |

---

## 갈린 의견 — 채택하지 않은 쪽을 남긴다

### 설비 이동 시 뺏어오기(steal)를 허용한다 — 도메인 관점의 409 제안을 기각

**03의 결정이지만 여기 남긴다.** 02가 만드는 API 형태가 03의 선택지를 좁히기 때문이다.

정수장 도메인 관점은 replace-all 본문에 **이미 다른 그룹에 편성된 설비**가 들어오면
**409로 거절**해야 한다고 봤다. 근거는 *"정수장에서 남의 계열 펌프가 소리 없이 빠지는 것은 사고다"* 이고,
운영자가 원인을 보도록 응답에 "이미 2계열에 편성됨"을 담아야 한다는 것이었다.

**뺏어오기 허용으로 결정했다.** 409로 막으면 "1계열 펌프를 2계열로 옮긴다"는
**가장 흔한 편성 변경**을 API가 표현하지 못하고, 운영자는 "A를 빈 목록으로 저장 → B에 저장"의
2요청을 강요당한다. 데이터 관점이 지적한 위험 구간이 정확히 거기 생긴다 —
두 트랜잭션 사이에 어느 그룹에도 속하지 않은 설비가 DB에 남는다.

**기각한 쪽의 우려는 실재한다.** 뺏어온 결과로 A그룹이 조용히 한 대 줄어들고 알림이 없다.
03에서 응답에 "이 요청으로 다른 그룹에서 빠진 설비 목록"을 실어 완화한다.

### `ems-service/CLAUDE.md`의 소유권 근거 문장에 반대가 있다

도메인 관점이 *"EMS의 송수펌프 제어가 그 값을 바꾸는 주체이기 때문"* 이라는 문장을 지목했다.
제어그룹 편성을 바꾸는 주체는 EMS 알고리즘이 아니라 **운영자(계장·운영팀)** 이고,
EMS가 바꾸는 것은 편성이 아니라 그 편성 위에서의 운전 상태라는 것이다.
소유 결론(ems 소유)에는 동의하며 정확한 근거는 *"EMS 에너지 최적화의 입력 축이기 때문"* 이라고 봤다.

**이번에 고치지 않는다** — CLAUDE.md 수정은 `/claude-md-sync`의 영역이다. 「다음 단계」에 제안으로 남긴다.

### 아키텍처 문서에 이 도메인의 근거가 거의 없다

도메인 관점이 발견해 실측으로 확인한 것이다.

| 키워드 | 아키텍처 문서 등장 |
|---|---|
| 제어그룹 | **0건** |
| 수계통 | **0건** |
| 송수펌프 | 1건 (934행) |

즉 [01](01-제어그룹-도메인-DDL.md)이 두 반대 관점을 뒤집고 5종 전부를 ems 소유로 판정할 때
근거로 삼은 것이 **934행 한 줄 전체**였다.

> *"(예: 밸브 개도 — autonomous는 송수를 제외한 공정을, ems는 송수펌프·밸브를 제어)."*

**결정을 되돌리지 않는다.** DDL이 이미 적용됐고 그 한 줄의 내용 자체는 유효하다.
다만 그 한 줄이 지탱하는 무게는 사실로 남긴다 — 아키텍처가 개정되어 그 문장이 바뀌면
제어그룹 5종의 소유권 판정이 함께 흔들린다.

---

## 변경 내역

| 대상 | 변경 |
|---|---|
| `.../ems/support/{ColLength,UseYn,EmsErrorCode}.java` | 신규 3파일 |
| `.../ems/ctrl/` | 신규 11파일 — `package-info` + `domain/CtrlGrp` + `repository` + `service` + `web` + `dto` 6종 |
| `.../ems/wnp/` | 신규 11파일 — 동형 |
| `apps/ems-service/build.gradle` | 테스트 의존 2행 추가(`spring-boot-resttestclient`·`spring-boot-restclient`), `testcontainers-junit-jupiter` 제거(「함정 기록」 12) |
| `.../test/.../AbstractIntegrationTest.java` | `RANDOM_PORT` + `@AutoConfigureTestRestTemplate`, **그리고 싱글턴 컨테이너로 전환**(아래 「함정 기록」 1) |
| `.../test/.../CtrlGrpSliceIntegrationTest.java` | 신규 — 16건 |
| `.../test/.../WnpSliceIntegrationTest.java` | 신규 — 6건 |
| `apps/ems-service/CLAUDE.md` | 「현재 상태 — DDL만 있고 코드가 없다」가 거짓이 되어 갱신 |
| `apps/CLAUDE.md` | 형 표에 ems 행 추가(01 「한계 7」이 비워 둔 자리) + 「통합 테스트 표준형」에 싱글턴 규칙 한 줄 |
| **`V1__ems_domain.sql`** | **변경 없음** — V2를 만들지 않는다(「이번 범위에서 뺀 것」) |
| **`application.yml`** | **변경 없음** — 스키마 배선은 01이 이미 했다 |
| **`infrastructure/docker/compose.yaml`** | **변경 없음** — 이번에 새로 필요해진 인프라가 없다. `depends_on`은 01이 이미 고쳤다 |
| **`EmsMigrationIntegrationTest.java`** | **변경 없음** — 기반 클래스만 바뀌었고 단언은 그대로 9건 통과 |
| **master-service** | **변경 없음** |
| **`config-repo/`** | **변경 없음** |

## 구현 상세

### 패키지 배치 (A형 2단)

```
com.mo.swtp.ems
├── EmsServiceApplication
├── ctrl/            제어그룹 — 편성 명세 3종(03)을 앞으로 함께 소유한다
│   └── {web,service,repository,dto,domain} + package-info
├── wnp/             수계통지점
│   └── {web,service,repository,dto,domain} + package-info
└── support/         ColLength · UseYn · EmsErrorCode
```

`query/`·`event/`는 만들지 않았다 — `apps/CLAUDE.md`가 "예약어 — 필요해질 때 만든다"로 정한 자리다.

### 수정 요청의 타입이 단건과 일괄로 갈린 이유

착수 후 정한 것이라 「결정」에 없다. **단건은 식별자를 담지 않고 일괄은 담는다.**

| 경로 | 타입 | 식별자 출처 |
|---|---|---|
| `PUT /api/ems/ctrl-grp/{ctrlGrpId}` | `CtrlGrpModifyRequest` | 경로 변수 |
| `PUT /api/ems/ctrl-grp/list` | `CtrlGrpModifyListRequest`(`CtrlGrpModifyItem` 목록) | 본문 |

master의 `TagController`는 `@PutMapping("/{tagSn}")`을 선언해 놓고 **`@PathVariable`을 받지 않는다** —
경로 변수가 아무 데도 쓰이지 않고 본문의 `tagSn`이 대상을 정한다. 즉 URL이 가리키는 자원과
실제로 바뀌는 자원이 다를 수 있고, 그 사실은 코드를 읽어야만 안다.
ems는 경로를 권위로 삼고 단건 요청에서 식별자를 빼 **둘이 어긋날 여지 자체를 없앴다.**

일괄 경로에는 대상을 가리킬 경로 변수가 없으므로 본문이 유일한 출처다 —
즉 식별자를 담는 이유가 단건과 정반대라서 타입을 나눴다.

### 등록 경로의 두 겹 방어

```
requireNoDuplicateWithin(ids)   요청 안에 같은 ID가 두 번 있는가
requireAbsent(ids)              DB에 이미 있는가
```

**앞의 것이 없으면 뒤의 것이 통과해 버린다.** 한 요청에 같은 ID가 두 번 오면 DB에는 아직 없으므로
존재 검사를 지나고, 뒤 항목이 앞 항목을 덮어쓴 결과만 남는다.
일괄 수정 쪽에서는 `Collectors.toMap`이 중복 키에 `IllegalStateException`을 던져 500이 되는 것도 함께 막는다.

## 함정 기록

1. **`@Testcontainers`/`@Container` 기준형이 통합 테스트 클래스 2개부터 깨진다.**
   싱글턴 전환 전 첫 빌드에서 **27건 중 14건이 `Connection refused`로 실패**했다.
   클래스별로 보면 원인이 드러난다 — 먼저 돈 `CtrlGrpSliceIntegrationTest`만 13/13 통과했고,
   뒤에 돈 `EmsMigrationIntegrationTest`(9/9)와 `WnpSliceIntegrationTest`(5/5)가 **전멸**했다.

   JUnit5 Testcontainers 확장은 static 컨테이너를 **테스트 클래스가 끝날 때 정지**시킨다.
   그런데 Spring 컨텍스트는 설정이 같으면 클래스 사이에 캐시되어 재사용되므로,
   두 번째 클래스는 **이미 죽은 컨테이너의 포트를 가리키는 DataSource**를 물려받는다.

   `@Testcontainers`/`@Container`를 떼고 static 초기화 블록에서 `start()`를 한 번만 부르는
   싱글턴으로 바꿔 고쳤다. 명시적 정지는 하지 않는다 — Ryuk가 JVM 종료 시 치운다.

   **처음 쓴 진단은 틀렸고 리뷰에서 정정했다.** 원래 *"통합 테스트 클래스가 둘 이상이면 깨진다"* 로
   적었는데, **`job-service`가 이미 클래스 2개인데 죽지 않는다.** 두 클래스가 서로 다른
   `@TestPropertySource`를 가져 **컨텍스트 캐시 키가 갈리고**, 컨텍스트가 따로 생기니 컨테이너 수명도
   따로 가기 때문이다. 실제 발동 조건은 *"둘 이상이 **같은 캐시된 컨텍스트를 공유**"* 다.
   틀린 조건을 그대로 뒀다면 `apps/CLAUDE.md`가 리포 안의 job-service를 깨진 것으로 규정하는 문장이
   됐을 것이다.

   **교훈: "기준형이 통과한다"와 "기준형이 옳다"는 다르다.** 이 기준형은 자기 결함이 드러나지 않는
   조건에서만 검증돼 있었고, `verifyIntegrationTestBaseline`은 파일 존재만 보므로 이것을 잡지 못한다.
   **강제 장치가 있어도 그 장치가 무엇을 보는지가 규약의 실질을 정한다.**

   > 병행 작업 중이던 master-service도 같은 시기에 독립적으로 같은 전환을 했다(작업트리에서 확인).
   > 두 작업이 같은 벽에 부딪혀 같은 처방에 도달했으므로 진단 자체의 신뢰도는 높다.
   > 다만 "누가 처음"인지는 커밋 순서 문제라 문서에 적지 않는다.

2. **히어독으로 긴 자바 파일을 쓰다 셸이 깨졌다.** 본문에 특수문자가 섞이면
   `unexpected EOF while looking for matching`으로 중간부터 파일이 안 만들어진다.
   부분적으로 생성된 상태가 남으므로, 실패 후에는 반드시 트리를 확인하고 재작성한다.
   서비스·컨트롤러처럼 긴 파일은 파일 쓰기 도구로 넘겼다.

3. **`cd`가 실패해도 다음 명령이 그대로 실행된다.** 앞선 셸의 작업 디렉토리가 남아 있어
   `cd`가 실패한 채 `cat > package-info.java`가 돌았고, `ctrl/`에 가야 할 파일이 `support/`에 생겼다.
   패키지 선언과 디렉토리가 어긋난 파일은 **컴파일 시점에야** 드러난다. 이후 전부 절대경로로 바꿨다.

4. **다른 도메인 클래스를 `{@link}`로 잇지 않는다.** `WnpRepository`의 주석에서
   `CtrlGrpRepository`를 링크했다가 되돌렸다. 바이트코드에는 남지 않지만
   `apps/CLAUDE.md`가 금지한 도메인 간 참조를 주석이 만드는 셈이고,
   **도메인을 떼어낼 때 그 링크가 함께 끊어진다.** 링크 없이 같은 내용을 적었다.

### 리뷰에서 나와 고친 것

5. **수정 응답의 `mdf_*`가 수정 이전 값이었다.** [양쪽 리뷰]
   감사 컬럼을 채우는 `AuditingEntityListener`의 `@PreUpdate`는 **flush 시점**에 발화하는데,
   서비스가 `replace()` 직후 아직 dirty인 엔티티로 응답을 만들고 있었다.
   **DB 행은 옳고 응답만 거짓**이라 더 찾기 어렵다.

   **하필 이 결함이 「결정 6」이 만든 노출면에 있었다.** master는 응답에 `rgstr_*`만 실어서
   겪은 적이 없고, "`mdf_*`는 이 도메인의 1급 정보"라며 일부러 추가한 자리에 바로 결함이 있었다.
   네 수정 경로 전부에 `flush()`를 넣어 고쳤다.

   **더 중요한 것은 기존 테스트가 이것을 잡지 못했다는 사실이다.** `감사_컬럼이_규약대로_움직인다`는
   요청이 끝난 뒤 `JdbcClient`로 DB를 읽는다 — DB는 옳으니 초록이다.
   즉 **검증 표의 항목 4는 참이었지만 그것이 응답의 정확성을 보증하지 않았다.**
   응답을 단언하는 테스트를 새로 넣고, **flush를 임시로 제거해 실제로 빨간불이 되는지 확인했다:**
   ```
   Expecting actual:   "2026-08-25T13:00:42.978118"
   not to be equal to: "2026-08-25T13:00:42.978118"
   ```
   응답의 `mdfDttm`이 등록 시각과 정확히 같았다. **통과하는 테스트와 잡아내는 테스트는 다르다.**

6. **수정 경로가 공백 이름을 통과시켰다.** [/code-review 중간]
   등록은 `@NotBlank`로 막는데 수정 DTO에는 `@Size`만 걸려 있었고, `replace()`는 "null이 아니면 설정"이라
   `{"ctrlGrpNm":"   "}`가 `NOT NULL` 컬럼에 들어갔다. **PostgreSQL은 빈 문자열을 받으므로 DB가 걸러 주지 않는다.**
   `@NotBlank`를 쓸 수 없다 — null이 "변경하지 않음"이라 허용해야 한다.
   `@Pattern(regexp = ".*\\S.*")`으로 **값이 왔을 때만** 공백만인 것을 막았다.

7. **등록 경합이 409가 아니라 500이 됐다.** [양쪽 리뷰]
   `requireAbsent`는 읽고 나서 쓰는(check-then-act) 구조라 원자적이지 않다. 같은 ID로 두 요청이
   동시에 오면 둘 다 존재검사를 통과하고 뒤엣것이 PK 위반으로 터지는데, flush를 커밋까지 미루면
   그 예외가 트랜잭션 경계 밖에서 나 catch-all이 `COMMON-500`으로 응답한다.
   **설계 전체가 409를 약속하는데 경합에서만 그 약속이 깨지는 상태였다.**
   함정 5의 `flush()`가 예외를 메서드 안으로 끌어오므로, 그것을 잡아 `EMS-409`로 되던지게 했다.

8. **일괄 수정 응답이 요청 순서가 아니었다.** [/code-review 낮음]
   `findAllById`의 반환 순서는 DB가 정한다. 요청과 응답을 위치로 짝지은 클라이언트가 값을 잘못 붙인다.
   요청 순서로 재구성했다.

9. **`CtrlGrpModifyItem`에 `Request` 접미사가 없었다.** [전용 리뷰]
   `apps/CLAUDE.md` 네이밍 표가 요청 타입을 `<도메인>*Request`로 못박았다.
   `*ModifyItemRequest`로 개명했다 — **규약을 넓히는 쪽이 아니라 이름을 맞추는 쪽을 택했다.**
   앱 커밋이 플랫폼 규약을 바꾸면 안 된다는 이번 문서의 원칙과 같은 이유다.

10. **wnp의 빈 목록 단언이 공허 참이었다.** [전용 리뷰]
    `assertThat(body.path("data")).isEmpty()`만 걸었는데, `path()`는 키가 없으면 `MissingNode`를 돌려주고
    그것도 "비어 있음"이라 **`data` 필드가 통째로 사라져도 초록**이었다.
    ctrl 쪽 같은 테스트는 `isArray()`를 먼저 단언해 이 구멍을 막고 있었다 —
    **한 결정(빈 목록 200)을 검증하는 두 테스트의 단언 강도가 갈려 있었던 것**이 놓친 이유다.
    01에서 *"한 테스트 클래스 안에서 단언 강도가 갈려 있었다"* 를 겪고도 이번엔 클래스 사이에서 재발했다.

11. **wnp 쪽 정렬이 검증되지 않았다.** [전용 리뷰]
    「결정 8」은 두 리포지토리 모두의 결정인데 wnp 테스트는 `sortOrd` 1·2만 써서
    **NULL도 동순위도 지나지 않았다.** 표에 wnp 정렬 행이 없어 거짓 주장은 아니었으나
    "두 리포지토리 모두 만족한다"가 실측으로는 절반만 뒷받침됐다. 같은 형태의 테스트를 추가했다.

12. **죽은 테스트 의존이 남아 있었다.** [전용 리뷰]
    `testcontainers-junit-jupiter`는 `@Testcontainers`/`@Container`를 뗀 뒤 쓰는 코드가 0건이다.
    남겨 두면 다음 사람이 기준형을 복제할 때 **깨진 확장을 다시 붙일 근거**가 된다. 제거했다.

13. **`BUILD SUCCESSFUL in 1s`가 테스트를 돌린 결과가 아니었다.**
    함정 5의 확인을 위해 뺐던 `flush()`를 원래 내용으로 정확히 되돌렸더니, Gradle이 입력 해시가
    일치한다고 보고 **이전 성공 실행의 출력을 캐시에서 복원**했다 — 테스트 결과 XML까지 그때 것이었다.
    직전 실행이 실패였는데 XML은 `failures="0"`을 말하고 있었다.
    `--rerun-tasks`로 다시 돌려 `23 actionable tasks: 23 executed`(캐시 복원 0건)와
    갱신된 타임스탬프까지 확인했다. **01이 남긴 "BUILD SUCCESSFUL과 테스트가 실제로 돈 것은 다르다"가
    이번엔 캐시 복원이라는 다른 경로로 재현됐다.**

## 알려진 한계

01에서 승계하는 것을 먼저 적는다. **해소되지 않은 것을 해소된 것처럼 옮기지 않는다.**

1. **`ctrl_eqp_p.eqp_id`·`ctrl_grp_tag_p.tag_sn`의 유효성 검증 장치가 **여전히 없다**.**
   [01](01-제어그룹-도메인-DDL.md) 「한계 4」를 그대로 승계한다. 이번에 넣은 `@NotBlank`·`@Size`는
   **형식 검증이지 존재 검증이 아니다.** 02의 범위(마스터 2종)에는 그 컬럼이 아예 없으므로
   위험이 실현되는 곳은 03이다. 그물은 현재 0겹이다 — 오타 ID로 편성이 등록돼도
   단독키 PK 때문에 중복 충돌조차 나지 않고, 터지는 곳은 제어 실행 시점이다.

2. **➖ 검증불가 — 컨테이너 E2E.** 01 「한계 3」을 승계한다. `docker compose up` 경로는 실행하지 않았다.
   Testcontainers는 `spring.flyway.create-schemas=true`로 스키마를 직접 만들지만,
   운영 경로는 `01-schemas.sql`이 만든 `ems` 스키마에 `create-schemas: false`로 붙는다.
   **그 차이는 이번 검증이 재현하지 못한다.**

3. **➖ 검증불가 — 게이트웨이 경유 경로.** 컨트롤러가 `/api/ems/ctrl-grp`를 노출하는 것은 확인했으나,
   외부 주소 `/ems-service/api/ems/ctrl-grp`가 `RewritePath`를 거쳐 실제로 도달하는지는
   게이트웨이를 띄우지 않아 확인하지 못했다. 라우트는 01 이전부터 있던 것이고 다른 6개 서비스가
   같은 형태로 동작 중이라 형태상 확실하나, **실행으로 확인한 것은 아니다.**

4. **➖ 검증불가 — OpenAPI 스키마 이름 충돌.** `apps/master-service/docs/01` 「한계 1」이 남긴
   숙제와 같은 자리다. `CtrlGrpAddRequest`·`WnpAddRequest`처럼 도메인 접두사를 붙여
   충돌을 애초에 없앴으나, 실제 스펙을 받아 확인하지는 못했다.
   ```bash
   curl -s localhost:8087/api/ems/v3/api-docs | jq '.components.schemas | keys'
   ```

5. **master의 같은 결함 4건을 고치지 않았다.** `addTag`·`addPrcs`·`addEqp`·`addFclt`가
   기존 ID 재등록 시 조용한 UPDATE가 되고(결정 5와 같은 구조), 목록 조회 4곳이 빈 결과에 404를 던진다(결정 4).
   **ems 커밋이 master 수정 커밋이 되면 안 되므로 손대지 않았다.** 「다음 단계」에 남긴다.

6. **`?useYn=y`(소문자)나 `?useYn=X`가 `COMMON-500`으로 응답한다.** [/code-review 중간 — 고치지 않음]
   Spring의 `StringToEnum`은 대소문자를 구분하므로 `MethodArgumentTypeMismatchException`이 나는데,
   `GlobalExceptionHandler`에 그 핸들러가 없어 catch-all이 500 + `log.error`로 잡는다.
   **클라이언트 잘못이 서버 장애로 보고되는 것**이고, 그 핸들러의 javadoc이 오타 URL에 대해 경고한
   바로 그 실패 형태다.

   **고칠 자리가 `swtp-web-starter`라 이번 범위에 넣지 않았다.** 11개 앱에 영향이 가는 공유 스타터이고
   자기 테스트와 리뷰를 따로 받아야 한다. master의 `EqpController`·`FcltController` 등도 같은 상태다 —
   즉 ems가 만든 결함이 아니라 플랫폼이 이미 갖고 있던 것이다.
   **그래도 이번에 전달한 API에 실재하는 결함이므로 감춰 두지 않는다.**

7. **ID가 `list`인 행은 단건 수정이 영영 안 된다.** [/code-review 낮음 — 고치지 않음]
   `ctrl_grp_id`가 운영자가 채우는 자유 문자열인데, Spring은 리터럴 경로를 템플릿보다 먼저 매칭하므로
   `PUT /api/ems/ctrl-grp/list`가 일괄 수정으로 라우팅된다. 본문이 `@NotEmpty`에 걸려 400이 되고
   그 행은 단건 수정 경로로 못 고친다.
   고치려면 예약어 검사를 새로 만들거나 일괄 경로를 `/batch`로 옮겨야 하는데,
   **`/list`는 master가 이미 쓰는 형태라 경로 규약 결정이 된다.** 실제 ID가 `CG-001` 꼴이라 발생
   가능성은 낮다고 보고 두었다.

8. **`sortOrd`를 NULL로 되돌릴 수 없다.** [/code-review 낮음 — 고치지 않음]
    `replace()`가 "null이면 변경하지 않음"이라 한 번 값이 들어가면 "미지정"으로 되돌릴 방법이 없다.
    정렬 규칙이 NULL을 의미 있게 구분하고(뒤로) 등록 경로는 NULL을 받으므로, API로 도달할 수 없는
    상태가 생긴다. 되돌리려면 센티널 값이나 `JsonNullable`이 필요한데
    **master의 `replace` 의미를 4개 서비스에서 함께 바꾸는 일**이라 비용이 크다.

9. **등록 경합은 409로 막았으나 수정 경합은 last-write-wins다.** `@Version`이 리포 전체에 0건이고
    이번에 도입하지 않았다. 두 사용자가 같은 제어그룹을 동시에 수정하면 나중 요청이 이긴다 —
    마스터 2종은 저빈도 관리 조작이라 실害가 낮다고 봤다. 편성(03)은 동시성 성격이 다르므로 거기서 다시 판단한다.

10. **`@Enumerated(STRING)` ↔ `bpchar` 타입 불일치는 그대로다.** 왕복이 동작하는 것은 검증했으나
   (검증 #1), Hibernate가 기대하는 것은 `VARCHAR`이고 실제 컬럼은 `bpchar`다.
   `ddl-auto: none`이라 기동 시 검증하지 않아 드러나지 않을 뿐이다.
   `@JdbcTypeCode(SqlTypes.CHAR)`를 붙이지 않았다 — `validate` 전환을 검토할 때 함께 다룰 일이고,
   그건 master 엔티티 6종까지 걸리는 별건이다.

## 검증

아래 항목은 **착수 전에 정했다.** 구현 후에 정하면 구현에 맞춰 기준이 내려간다.
리뷰에서 추가된 항목(4'·7''·9)은 그 자리에 표시했다.

```bash
./gradlew :apps:ems-service:build
```

| # | 무엇이 참이면 완료인가 | 어떻게 |
|---|---|---|
| 1 | `CtrlGrp` 저장 후 `use_yn`이 `'Y'`로 들어가고 `UseYn.Y`로 되읽힌다 | REST 등록 → SQL로 raw 값 확인 → REST 조회 (bpchar 왕복, 결정 1 ①) |
| 2 | **이미 있는 ID로 등록하면 409다** — 조용한 UPDATE가 아니다 | 같은 ID 2회 POST. `rgstr_dttm`이 1회차 값 그대로인지 SQL로 확인 (결정 5) |
| 3 | `rgstr_id`·`mdf_id`에 **토큰 `sub`가 박힌다** — `SYSTEM`이 아니다 | `SwtpTestJwt.accessToken("tester", …)` 발급 후 등록 → SQL 확인 |
| 4 | 등록 직후 `mdf_dttm == rgstr_dttm`이고, 수정 후에는 달라진다 | step-14가 정한 "한 번도 수정되지 않음" 판정 규칙 |
| 5 | 목록 조회가 **비어 있어도 200 + 빈 배열**이다 | 데이터 0건 상태에서 GET (결정 4) |
| 6 | `sort_ord`가 NULL인 행이 뒤로 가고 순서가 결정적이다 | NULL·중복 섞어 등록 후 2회 조회해 동일 순서 확인 (결정 8) |
| 7 | 토큰 없이 호출하면 401 + `COMMON-401` 봉투다 | 게이트웨이 우회 직접 호출 방어 |
| 8 | `verifyIntegrationTestBaseline`이 통과한다 | Gradle `check` 의존 |

### 실측 결과 (2026-08-25, 리뷰 반영 후 재실행)

**캐시 복원이 실행으로 읽히지 않도록 `--rerun-tasks`로 돌렸다**(「함정 기록」 13).

```
> Task :apps:ems-service:compileJava
> Task :apps:ems-service:compileTestJava
> Task :apps:ems-service:verifyIntegrationTestBaseline
> Task :apps:ems-service:test
> Task :apps:ems-service:check
> Task :apps:ems-service:build

BUILD SUCCESSFUL in 38s
23 actionable tasks: 23 executed
```

`23 executed`에 `from-cache`·`up-to-date`가 하나도 없다는 것이 **전부 실제로 돌았다는 증거**다.

**`BUILD SUCCESSFUL`과 테스트가 실제로 돈 것은 다르므로** XML 리포트의 집계를 함께 붙인다
(`build/test-results/test/`, `timestamp="2026-08-25T04:01:39.302Z"`).

```
CtrlGrpSliceIntegrationTest.xml    tests="16" skipped="0" failures="0" errors="0"
EmsMigrationIntegrationTest.xml    tests="9"  skipped="0" failures="0" errors="0"
WnpSliceIntegrationTest.xml        tests="6"  skipped="0" failures="0" errors="0"
```

| # | 검증 항목 | 테스트 메서드 | 실제 단언하는 것 | 결과 |
|---|---|---|---|---|
| 1 | bpchar enum 왕복 | `사용여부가_bpchar로_왕복한다` | **SQL로 raw 값이 정확히 `'N'`**(패딩 없음) + REST 재조회 | ✅ |
| 1' | 기본값 | `사용여부_기본값은_Y다` | 생략 시 `Y`, `sortOrd`는 NULL 유지(0으로 채워지지 않음) | ✅ |
| 2 | 중복 등록 409 | `중복_등록은_409로_끊긴다` | `EMS-409` + **감사 4컬럼이 1회차와 동일** + 이름이 안 바뀜 | ✅ |
| 2' | 요청 내부 중복 | `요청_내부_중복도_409다` | 409 + 테이블 행 수 0 | ✅ |
| 3 | 감사 주체 = 토큰 `sub` | `감사_주체가_토큰_주체다` | `rgstr_id`·`mdf_id`가 `tester`(`SYSTEM` 아님) | ✅ |
| 4 | 감사 컬럼 규약 | `감사_컬럼이_규약대로_움직인다` | 등록 시 `mdf_dttm == rgstr_dttm`, 수정 후 달라짐, `rgstr_dttm` 불변 — **DB 기준** | ✅ |
| 4' | **수정 응답의 감사값** | `수정_응답이_갱신된_감사값을_싣는다` | 응답의 `mdfDttm`이 등록 시각과 다름 + `mdfId`가 토큰 주체 + DB도 갱신됨 — **응답 기준**(「함정 기록」 5) | ✅ |
| 4'' | 수정 대상 권위 | `수정_대상은_경로가_정한다` | 경로가 가리킨 것만 바뀌고 옆 행은 그대로 | ✅ |
| 5 | 빈 목록 200 | `빈_목록은_오류가_아니다` (ctrl·wnp 각 1) | `SUCCESS` + **`isArray()` 선단언** + 비어 있음 | ✅ |
| 5' | 단건 404 | `없는_단건은_404다` | `COMMON-404` | ✅ |
| 6 | 정렬 결정성 | `정렬이_결정적이다` (ctrl·wnp 각 1) | NULL이 뒤, 동순위는 ID순, **2회 조회 결과가 동일** | ✅ |
| 6' | 사용여부 필터 | `사용여부로_거른다` | `?useYn=Y`/`N`이 각각 하나씩 | ✅ |
| 7 | 미인증 401 | `토큰이_없으면_401이다` (ctrl·wnp 각 1) | 401 + `COMMON-401` 봉투 | ✅ |
| 7' | 형식 검증(등록) | `형식_검증이_걸린다` | `COMMON-400` + `field=ctrlGrpNm` + 행 수 0 | ✅ |
| 7'' | **형식 검증(수정)** | `공백_이름은_수정으로도_들어가지_못한다` | 400 + **값이 안 바뀌었음을 DB 조회로 확인**(「함정 기록」 6) | ✅ |
| 8 | 기준형 규약 | `verifyIntegrationTestBaseline` | `--rerun-tasks`로 실제 실행 | ✅ |
| 9 | **일괄 응답 순서** | `일괄_수정_응답이_요청_순서다` | 요청을 `[A2, A1]` 순으로 보내면 응답도 `[A2, A1]`(「함정 기록」 8) | ✅ |
| (wnp) | 독립 표면 | `독립_표면을_갖는다` | 별도 경로 등록·조회 + **다른 사용자 토큰**이 감사에 반영 | ✅ |
| (wnp) | 일괄 경로 | `일괄_경로가_동작한다` | 일괄 등록·수정, 등록 순서가 아니라 정렬순서로 조회됨 | ✅ |
| (wnp) | 일괄 선검사 | `일괄_수정은_전부_아니면_전무다` | 404 + **값이 안 바뀌었음을 실제 조회로 확인** | ✅ |

**"실제 단언하는 것" 열을 따로 두는 이유**는 01이 겪은 실패 때문이다 — 그때 항목 3·4는 ✅였지만
단언이 주장보다 좁아 컬럼이 통째로 사라져도 통과하는 공허 참이었다.
`tests="31" failures="0"`은 그것을 알려주지 않는다. **이번에도 같은 유형이 두 건 나왔다**
(「함정 기록」 5·10) — 표를 쓰는 것만으로는 부족하고, 표의 주장과 코드를 대조해야 잡힌다.

특히 #2는 **409를 받았다는 것만으로 단언하지 않는다.** 감사 컬럼을 등록 전후로 비교해
`merge()` 경로의 조용한 UPDATE가 실제로 일어나지 않았음까지 확인한다.

#4'는 **`flush()`를 임시로 제거해 실제로 빨간불이 되는지 확인했다** — 통과하는 테스트와
잡아내는 테스트는 다르기 때문이다. 그때의 실패 출력은 「함정 기록」 5에 있다.

> **표에서 뺀 주장 하나.** 초안은 wnp의 `일괄_수정은_전부_아니면_전무다`를 *"롤백을 실제 값으로 확인"* 이라고
> 적었는데, 현재 구현은 **변경을 가하기 전에** 크기 비교로 끊으므로 트랜잭션 롤백을 밟지 않는다.
> 단언 자체는 참이지만 검증하는 것은 "선검사 순서"다. 문구를 실제 경로에 맞췄다(전용 리뷰 지적).
> 구현이 순서를 바꾸면 그때는 롤백을 잡으므로 회귀 그물로서의 가치는 그대로다.

## 다음 단계

1. **03 — 편성 3종 replace-all API.** 이번 결정 1이 미룬 것. flush 경계·이동 규칙·동시성을 다룬다.
2. **ems→master 동기 HTTP 클라이언트 규약** — 별도 `/step`. 분산 관점이 순서를 지정했다:
   **제어 실행 코드보다 먼저 와야 한다**(위험이 실현되는 시점이 제어 실행이므로).
3. **master의 목록 조회 404** — 결정 4에서 갈라진 자리. `getTagList`·`getPrcsList`·`getEqpList`·`getFcltList`
   4곳이 빈 결과에 404를 던진다. 별건.
4. **master의 등록 경로 잠복 결함** — 결정 5와 같은 구조로 `addTag`·`addPrcs`·`addEqp`·`addFclt`가
   기존 ID 재등록 시 조용한 UPDATE가 된다. 별건.
5. **`ems-service/CLAUDE.md`의 소유권 근거 문장 정정 제안** — 「갈린 의견」 참조. `/claude-md-sync` 영역.
6. **`swtp-web-starter`에 `MethodArgumentTypeMismatchException` 핸들러 추가** — 「알려진 한계」 6.
   잘못된 enum 쿼리 파라미터가 500이 되는 것을 400으로 돌린다. 공유 스타터라 별건이고,
   고치면 ems뿐 아니라 master의 컨트롤러 4종도 함께 낫는다.
7. **통합 테스트 기준형의 싱글턴 전환을 남은 앱에 전파** — auth·job·telemetry·realtime.
   지금은 컨텍스트 캐시 키가 갈려 있어 드러나지 않을 뿐이고, 그 앱들이 같은 설정의 테스트 클래스를
   하나 더 추가하는 순간 밟는다. master는 병행 작업에서 이미 전환됐다.
