# master-service 05 — API 명세 적용

- 일자: 2026-08-25
- 상태: ✅ 완료
- 검토 관점: 없음 — 판정표상 소환 0명(신규 테이블·Kafka·compose 변경 없음, 불변식 무충돌)
- 리뷰: 미실시 — 「다음 단계」 참조
- 관련: [01 패키지 규약](01-패키지-규약.md) · [04 운전모드 변경이력 조회](04-운전모드-변경이력-조회.md) · `apps/CLAUDE.md` 「API 문서화」 · [ems 04](../../ems-service/docs/04-수계통지점-API-명세.md)

## 발단

커밋 `841df51`이 OpenAPI 명세 규약을 세우면서 ems `ctrl` 슬라이스만 기준 구현체로 적용했다.
**master-service는 swagger 애노테이션이 0건이었다.**

규약 본문이 이 앱을 이름으로 지목하고 있었다:

> 그래서 검증 애노테이션이 없는 도메인은 스펙도 부실하다. master의 `tag`·`eqp`·`prcs`·`fclt`가
> 그 상태다 — **그 도메인을 문서화하는 커밋이 `@NotBlank`·`@Size`를 함께 넣는 커밋이다.**

즉 이 작업은 착수 시점부터 "문서만 다는 작업"이 될 수 없었다.
착수 전 조사에서 결함 4건이 추가로 드러났고, 그중 둘을 범위에 넣기로 사용자와 합의했다.

## 결정 (2026-08-25)

### 1. 범위를 셋으로 자른다 — 문서화 + 검증 + 빈 목록 계약

| 포함 | 제외 |
|---|---|
| OpenAPI 애노테이션 (컨트롤러 5 · DTO 22) | `PUT /{id}`의 `@PathVariable` 미선언 4곳 |
| `@NotBlank`·`@Size`·`@Valid` 추가 | 등록 시 중복 ID 409 방어 |
| 목록 조회 빈 결과 404 → 빈 배열 200 | |

**제외한 둘은 문서화하면 스펙에 드러난다.** 감추지 않고 `@Operation(description)`에 현재 동작을
사실대로 적었다 — "경로의 {id}는 현재 무시된다", "이미 있는 ID로 등록하면 조용히 수정된다".
규약의 최우선 원칙이 *"스펙에 거짓을 싣지 않는다"*이기 때문이다. 후속 근거는 「알려진 한계」에 남긴다.

### 2. 빈 목록 404를 걷어낸다 — 단, 404를 세 종류로 갈라서

ems가 같은 자리에서 *"결과가 없으면 빈 배열과 함께 200이다(404가 아니다). 신규 정수장은 0건에서
출발하므로 '아직 없음'과 '조회 실패'를 구분할 수 있어야 한다"*를 명시적 계약으로 두고 있었고,
**같은 앱 안의 `DrvmdChgService`가 이미 같은 근거를 적어 두고 있었다**:

> 같은 앱의 다른 도메인(`PrcsService` 등)은 빈 목록을 `NOT_FOUND`로 바꾸지만 여기서는 의미가 다르다
> — 이력이 없는 것은 "아직 한 번도 안 바뀜"이라는 정상 상태다. 404로 바꾸면 프론트가 정상 상태를
> 오류로 표시하게 된다.

즉 뒤집는 결정이 아니라 **같은 앱 안의 불일치를 한쪽으로 정렬하는 결정**이다.
무차별 치환이 아니라 셋으로 갈랐다:

| 자리 | 처리 | 근거 |
|---|---|---|
| 목록 조회 `if(list.isEmpty()) throw NOT_FOUND` | **삭제** | 0건은 정상 상태다 |
| 타입별·시설별 조회 `Optional<List<>>.orElseThrow` | **정리** | 죽은 코드였다(아래) |
| 수정 대상 없음 / 단건 조회 없음 | **유지** | 지목한 자원이 없는 것은 실제 오류다 |

**`Optional<List<T>>.orElseThrow`는 한 번도 발화하지 않는 죽은 코드였다.**
Spring Data JPA는 컬렉션 반환 파생 쿼리를 `Optional`로 감싸도 결과가 없으면 `Optional.empty()`가
아니라 **빈 리스트를 담은 `Optional`**을 준다. 즉 그 엔드포인트들은 이전에도 실질적으로 빈 배열 200이었고,
남겨 두면 "이 엔드포인트가 404를 낼 수 있다"는 거짓이 스펙에 실린다.
리포지토리 시그니처를 `List<T>`로 바꿔 "404를 내는가"에 답할 수 있게 만들었다.

### 3. `limit` 범위 검사를 Bean Validation으로 옮기지 않는다

`DrvmdChgController.getHistory`의 `limit`(1~1000)은 스펙에는 `@Min`·`@Max`가 깔끔하다.
그런데 **옮기면 400이던 응답이 500이 된다** — 클래스에 `@Validated`를 걸고 파라미터 제약을 쓰면
위반이 `ConstraintViolationException`이 되는데, `GlobalExceptionHandler`가 그 타입을 다루지 않아
catch-all(`COMMON-500`)로 빠진다. 서비스의 명시적 검사를 그대로 두고 범위는 `@Parameter` 설명에 실었다.

**스펙을 예쁘게 만들려다 런타임 계약을 망가뜨리는 교환**이라 기각했다. 근거를 컨트롤러 Javadoc에 남겼다.

### 4. `ColLength`에 상수를 채우고 엔티티 리터럴을 없앤다

`@Size(max = ...)`를 달려면 길이의 출처가 필요한데, `eqpNm`(50)·`eqpTypeCd`(20)·`tagTypeCd`(3) 등이
**엔티티에 리터럴로 박혀 있었다.** 이 앱 CLAUDE.md가 *"컬럼 길이를 엔티티마다 리터럴로 적지 않는다"*고
이미 규정하고 있었으므로, 상수를 추가하는 김에 엔티티도 상수를 보게 바꿨다.

상수만 추가하고 엔티티를 그대로 뒀다면 **같은 값의 출처가 둘**이 되어, 한쪽만 고쳤을 때
검증을 통과한 값이 DB에서 잘린다. 값의 SSOT는 여전히 `V2__master_domain.sql`이다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `support/ColLength.java` | 상수 7개 추가 — `TAG_TYPE_CD`·`{EQP,PRCS,FCLT}_{NM,TYPE_CD}` |
| `{tag,eqp,prcs,fclt}/domain/*.java` | `@Column(length = 리터럴)` → `ColLength.*` (7곳) |
| `{tag,eqp,prcs,fclt}/repository/*.java` | `Optional<List<T>>` → `List<T>` (5개 메서드) |
| `{tag,eqp,prcs,fclt}/service/*.java` | 목록 조회 404 제거(4곳) · 죽은 `orElseThrow` 제거(5곳) · 계약 Javadoc 추가 |
| `{tag,eqp,prcs,fclt}/web/*Controller.java` | `@Tag` 4 · `@Operation` 29 · `@Parameter` · FQN `@ApiResponse` · **`@Valid` 29곳** |
| `drvmd/web/DrvmdChgController.java` | `@Tag` 1 · `@Operation` 2 · `@Parameter` 8. `@param` Javadoc → `@Parameter` 이동 |
| `{tag,eqp,prcs,fclt,drvmd}/dto/` 22종 | `@Schema` 전 컴포넌트 · 검증 애노테이션(drvmd 2종은 응답 전용이라 `@Schema`만) |
| `build.gradle` | **변경 없음** — springdoc은 `swtp-web-starter`가 `api`로 전파한다 |
| 테스트 | **변경 없음** — CRUD 4종 통합 테스트가 애초에 없다(아래 「알려진 한계」 4) |

## 함정 기록

**1. swagger `@Tag`와 도메인 엔티티 `Tag`의 단순명 충돌은 실제로는 일어나지 않았다.**
`TagController`가 엔티티를 직접 참조하지 않아 애노테이션 쪽을 그대로 import하면 됐다.
다만 나중에 엔티티를 들여오면 충돌하므로, **빈도가 낮은 쪽(애노테이션)을 FQN으로 미룬다**는
판정 근거를 클래스 Javadoc에 미리 남겼다.

**2. `@ApiResponse`는 전 컨트롤러에서 FQN으로 썼다.** 공용 응답 봉투 `com.mo.swtp.common.api.ApiResponse`가
모든 핸들러의 반환 타입에 나오므로, 빈도가 낮은 애노테이션 쪽을 FQN으로 미뤘다(규약).

**3. `modifyXxx`에 `flush()`가 없는 것은 결함이 아니다.** ems는 `mdf_dttm`이 수정 이전 값으로
나가는 것을 막으려 `flush()`를 명시했는데, master의 응답 DTO는 `mdfDttm`·`mdfId`를 **애초에 싣지 않는다**
(`rgstrDttm`·`rgstrId`만). 갱신 이전 값이 응답에 나갈 경로가 없다. 조사 중 결함 후보로 올랐다가 기각했다.

**4. 공통 400·401·500은 한 줄도 적지 않았다.** 스타터의 `swtpCommonErrorResponsesCustomizer`가
전 operation에 주입하며, `addIfAbsent`라 컨트롤러가 선언한 404·409는 덮이지 않는다.

**5. 검증 추가로 실제 응답이 바뀐다.** 이전에는 검증 실패가 DB 제약 위반까지 내려가 `COMMON-500`이었고,
`@Valid` 부착 후 `GlobalExceptionHandler.handleValidation`이 살아나 `COMMON-400` + 필드별 오류 목록이 된다.
문서 작업의 부수효과가 아니라 규약이 의도한 정상 동작이다.

## 알려진 한계

**1. `PUT /{id}`가 경로 변수를 무시한다 (tag·eqp·prcs·fclt 4곳).**
메서드가 `@PathVariable`을 선언하지 않아 대상은 본문의 식별자로만 정해진다 —
`PUT /api/master/tag/A`에 `{"tagSn":"B"}`를 보내면 **B가 수정된다.**

구조적 원인은 **`*ModifyRequest` 하나를 단건 경로와 일괄 경로가 공유**하는 것이다.
일괄 경로에는 대상을 가리킬 경로 변수가 없어 DTO가 식별자를 갖게 됐고, 그것이 단건 경로에서도 이겼다.
ems는 같은 자리에서 `ModifyRequest`(식별자 없음)와 `ModifyItemRequest`(식별자 있음)로 쪼개 이 문제를 없앴다.

고치려면 DTO 분리가 따라오므로 이번 범위에서 뺐다. **현재 동작은 `@Operation(description)`에 명시했다.**

**2. 등록에 중복 방어가 없다 — 조용한 UPDATE가 된다.**
`save()`가 assigned-ID + `Persistable` 미구현이라 `merge()`를 타므로, 기존 ID로 등록해도 예외 없이 UPDATE다.
일괄 등록은 `saveAll`뿐이라 요청 안의 중복도 통과하고 뒤의 것만 남는다.
ems는 `requireAbsent`·`requireNoDuplicateWithin`·`saveFlushing` 세 방어로 409를 낸다.

**일괄 수정은 더 나쁘다** — `Collectors.toMap`이 중복 키에 `IllegalStateException`을 던져 `COMMON-500`이 된다.
`@Operation`에 "같은 식별자를 두 번 담으면 안 된다"고 적었으나, 스펙에 적는 것으로 방어를 대신할 수는 없다.

**3. 목록 정렬이 결정적이지 않다.**
`eqp`·`prcs`·`fclt`는 `OrderBySortOrdAsc`인데 `sortOrd`가 nullable이고 타이브레이커가 없어
**같은 정렬순서끼리의 순서가 보장되지 않는다.** `tag`는 더해서 `findAll()`이라 **정렬 자체가 없다**.
ems는 `order by sort_ord asc nulls last, <id> asc`로 확정해 뒀다.
스펙에 "정렬이 보장된다"고 쓸 수 없어 사실대로 "보장되지 않는다"고 적었다.

**4. CRUD 4종에 통합 테스트가 없다.**
이번 계약 변경(빈 목록 200, 검증 400)이 **기존 테스트를 하나도 깨지 않은 이유**가 이것이다.
반대로 말하면 그 계약을 잠그는 장치도 없다 — 다음 사람이 되돌려도 빌드가 알려주지 않는다.

**5. 일괄 수정의 응답 순서가 요청 순서와 다를 수 있다.**
`findAllById`의 반환 순서가 DB가 정하는 대로다. ems는 요청 순서로 재정렬해 돌려준다.
"위치로 짝짓지 말고 식별자로 맞추라"고 `@Operation`에 적었다.

## 검증

```bash
# 1. 빌드 (Testcontainers → Docker Desktop 필요)
./gradlew :apps:master-service:build

# 2. 스펙 실물 — 스키마 이름 유일성 + 공통 에러 주입 (01 「한계」의 미결 실측)
./gradlew :apps:master-service:bootRun
curl -s localhost:8081/api/master/v3/api-docs | jq '.components.schemas | keys'

# 3. 계약 변경 확인
curl -i localhost:8081/api/master/tag                      # 빈 목록 → 200 + []  (이전 404)
curl -i -X POST localhost:8081/api/master/tag \
     -H 'Content-Type: application/json' -d '{}'           # 검증 실패 → COMMON-400 (이전 500)

# 4. 스타터 계약 회귀 (영향받으면 안 된다)
./gradlew :starters:swtp-web-starter:test :apps:gateway:test
```

### 실측 결과 (2026-08-25)

**빌드** — `./gradlew :apps:master-service:build` 성공 (통합 테스트 포함, Testcontainers 기동).

**엔드포인트별 응답 코드** — 31개 operation 전부가 200을 갖는다. 404는 선언한 자리에만 있다.
```
/api/master/tag              [get]  → 200,400,401,500        ← 목록: 404 없음 (교정 전에는 404를 냈다)
/api/master/tag/type/{...}   [get]  → 200,400,401,500        ← 죽은 orElseThrow 제거
/api/master/tag/{tagSn}      [get]  → 200,400,401,404,500    ← 단건: 404 유지
/api/master/tag/{tagSn}      [put]  → 200,400,401,404,500
/api/master/tag/list         [put]  → 200,400,401,404,500
/api/master/drvmd            [get]  → 200,400,401,500
(eqp·prcs·fclt도 같은 형태. eqp/fclt/{fcltId}는 200,400,401,500)
```

**스키마 이름 — 충돌 없음.** `01-패키지-규약.md` 「한계」의 미결 실측이 해소됐다.
봉투 특수화를 뺀 22개가 전부 도메인 접두사로 유일하다.

**검증 애노테이션이 스펙을 만든다** — 손으로 적은 것은 `description`뿐이다.
```json
"TagAddRequest": {
  "properties": {
    "tagSn":     { "maxLength": 30, "description": "태그시리얼번호. ..." },
    "tagTypeCd": { "maxLength": 3,  "description": "태그타입코드" },
    "useYn":     { "enum": ["Y","N"], "description": "사용여부. 생략하면 Y로 등록된다" }
  },
  "required": ["tagSn", "tagTypeCd"]
}
```
`required`는 `@NotBlank`가, `maxLength`는 `@Size`가, `enum`은 타입이 유도했다.
식별자·감사 컬럼에 `example`이 하나도 없는 것도 확인했다(`TagResponse` 5개 필드 전부 `example` 없음).

**런타임 계약 변경 — 4건 전부 설계대로**
```
GET  /api/master/tag              → 200  {"code":"SUCCESS","data":[]}          (교정 전 404)
GET  /api/master/tag/type/ZZZ     → 200  {"code":"SUCCESS","data":[]}          (죽은 orElseThrow 자리)
POST /api/master/tag  본문 {}     → 400  {"code":"COMMON-400","data":[
                                          {"field":"tagSn","reason":"공백일 수 없습니다"},
                                          {"field":"tagTypeCd","reason":"공백일 수 없습니다"}]}   (교정 전 500)
GET  /api/master/tag/NO-SUCH-TAG  → 404  {"code":"COMMON-404","data":null}     (유지)
```
1번은 DB에 태그가 0건인 상태에서 받은 응답이다 — **교정 전이라면 404가 나왔을 바로 그 상황**이다.

### 이번 측정이 드러낸 것 (2026-08-25)

첫 측정에서 **`@ApiResponse`를 선언한 엔드포인트의 200이 통째로 사라져 있었다.**
이 앱만의 문제가 아니라 규약의 기준 구현체(ems `ctrl`)도 같은 상태였다.
원인·교정·기각한 대안은 [starters 01](../../../starters/docs/01-OpenAPI-성공응답-복원.md)에 있다.
**이 앱의 코드는 그 교정 때문에 한 줄도 바뀌지 않았다.**

## 다음 단계

- **리뷰를 아직 돌리지 않았다** — 여러 모듈(master·ems·starters)에 걸치고 공유 스타터를 건드렸으므로
  `/step` 발동 조건 두 개에 해당한다.
- 「알려진 한계」 1·2(경로 변수 무시, 중복 방어 없음)는 각각 DTO 분리와 서비스 방어 추가가 필요하다.
  ems `ctrl`/`wnp`에 이미 선례가 있으므로 그것을 옮겨 오는 작업이 된다.
- 「알려진 한계」 3(정렬 비결정성) — `nulls last` + 식별자 타이브레이커. ems 리포지토리가 선례다.
- 「알려진 한계」 4(CRUD 통합 테스트 부재) — 이번에 바꾼 계약을 잠그는 장치가 없다.
- **`apps/CLAUDE.md:180`의 끊어진 참조** — `apps/docs/01-API-문서화-규약.md`를 가리키는데 그 디렉토리가 없다.
  규약 본문은 `apps/CLAUDE.md`에 인라인으로 들어 있고, 근거의 실제 소재는
  `docs/scaffold/steps/step-11-api-docs.md`와 커밋 `841df51`이다.
  착수 계획에는 이번에 바로잡는 것으로 넣었으나 **하지 않았다** — `/step`은 CLAUDE.md 편집을
  `/claude-md-sync`의 영역으로 두므로, 여기서는 제안으로만 남긴다.
