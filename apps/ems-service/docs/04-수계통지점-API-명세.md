# ems-service 04 — 수계통지점 API 명세

- 일자: 2026-08-25
- 상태: ✅ 완료
- 검토 관점: 없음 — 판정표상 소환 0명(신규 테이블·Kafka·compose 변경 없음, 불변식 무충돌, 기존 결정 답습)
- 리뷰: 미실시 — 「다음 단계」 참조
- 관련: [02 제어그룹 마스터 CRUD](02-제어그룹-마스터-CRUD.md) · [03 제어그룹 편성 API](03-제어그룹-편성-API.md) · `apps/CLAUDE.md` 「API 문서화」

## 발단

커밋 `841df51`이 OpenAPI 명세 규약을 세우면서 **`ctrl` 슬라이스에만** 적용했다.
같은 앱의 `wnp` 슬라이스 7개 파일(컨트롤러 1 + DTO 6)에는 swagger 애노테이션이 하나도 없어,
swagger-ui에서 제어그룹은 계약이 보이고 수계통지점은 이름만 보이는 상태였다.

**swagger-ui가 프론트에게는 유일한 API 계약 문서다.** 한 앱 안에서 절반만 문서화된 상태는
"문서가 없다"보다 나쁘다 — 프론트가 `ctrl`의 상세함을 보고 `wnp`도 같은 수준이라고 가정하게 된다.

## 결정 (2026-08-25)

### 1. `ctrl`의 문장을 도메인명만 바꿔 대응시킨다

두 슬라이스의 CRUD 표면이 완전 동형이다(목록·단건·등록·일괄등록·단건수정·일괄수정 6개).
`WnpService`의 예외 경로를 실제로 읽어 `ctrl`과 1:1로 대응하는지 확인한 뒤 적용했다 —
문장을 옮기기 전에 **코드가 정말 같은 계약인지**를 먼저 봤다.

| 핸들러 | 실제 예외 경로 | 스펙 |
|---|---|---|
| 목록 조회 | 없음 | 빈 배열 200 |
| 단건 조회 | `getEntity` → `COMMON-404` | 404 |
| 등록 | `requireAbsent` / `saveFlushing` → `EMS-409` | 409 |
| 일괄 등록 | `requireNoDuplicateWithin` + `requireAbsent` → `EMS-409` | 409 |
| 단건 수정 | `getEntity` → `COMMON-404` | 404 |
| 일괄 수정 | 중복 → `EMS-409`, 크기 불일치 → `COMMON-404` | 409 · 404 |

### 2. `@Tag`는 `ctrl`과 공유하지 않고 새로 만든다

`CtrlGrpMemberController`는 `CtrlGrpController`와 `@Tag(name = "제어그룹")`을 공유한다 —
편성이 제어그룹 상세 화면의 일부이기 때문이다. **수계통지점은 그렇지 않다.**
02 「결정 2」가 두 도메인을 가른 근거(변경 주기 차이)가 화면 분리와 같은 축이라,
프론트가 지점 목록을 찾을 자리는 제어그룹 묶음 안이 아니라 자기 묶음이다.

### 3. Javadoc의 계약 문장은 옮긴다 — 복사가 아니다

규약이 *"같은 문장을 양쪽에 적지 않는다"*로 못박은 지점이다. 다음 둘을 `@Operation`으로 옮기고
Javadoc에서 **뺐다**.

- `WnpController:43` "결과가 없으면 빈 배열 200이다(404가 아니다)"
- `WnpController:64` "대상은 경로가 정한다. 본문에 식별자를 두지 않아 둘이 어긋날 여지를 없앤다"

클래스 Javadoc의 도메인 분리 근거(왜 `ctrl`과 별개 표면인가)는 개발자용이라 그대로 뒀다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `wnp/web/WnpController.java` | `@Tag` 1 · `@Operation` 6 · `@Parameter` 4 · FQN `@ApiResponse` 6. 계약 문장 2건을 Javadoc→애노테이션 이동 |
| `wnp/dto/` 6종 | `@Schema(description)` — record 타입과 전 컴포넌트 |
| `wnp/service/WnpService.java` | **변경 없음** — 계약을 읽기만 했다 |
| `wnp/repository/WnpRepository.java` | **변경 없음** — 정렬 계약을 읽기만 했다 |
| `build.gradle` | **변경 없음** — springdoc은 `swtp-web-starter`가 `api`로 전파한다 |

## 함정 기록

**1. 검증 애노테이션이 이미 갖춰져 있어 `@Schema`가 순수 추가였다.**
`@NotBlank`·`@Size(max = ColLength.*)`·`@Pattern`이 이미 있어 `required`·`maxLength`가 자동 유도된다.
규약대로 `@Schema(requiredMode=)`·`@Schema(maxLength=)`를 **적지 않았다.**
master의 같은 작업이 검증 추가부터 해야 했던 것과 대비된다(master 05).

**2. `example`은 `sortOrd`에만 붙였다.** 식별자(`wnpId`)와 감사 컬럼(`rgstrId`·`mdfId`)에는 넣지 않았다 —
ID 체계가 정수장마다 달라 예시가 형식 규칙으로 읽힌다.

**3. 정렬 계약을 리포지토리에서 확인하고 적었다.**
`WnpRepository`가 `order by w.sortOrd asc nulls last, w.wnpId asc`로 **결정적 순서를 명시**하고 있어
"같은 요청은 항상 같은 순서를 돌려준다"를 스펙에 쓸 수 있었다.
master의 같은 자리는 타이브레이커가 없어 그 문장을 쓸 수 없었다(master 05 「알려진 한계」 3).

## 알려진 한계

**1. `@SecurityRequirements` 사용처가 여전히 0건이다.** ems에는 공개 핸들러가 없어 해제할 자리가 없다.
스타터가 건 전역 bearer 요구가 전 핸들러에 그대로 적용되는 것이 맞다.

**2. OpenAPI 스키마 이름 충돌은 이번 실측으로 확인한다.** 02·03의 미결 항목이었다 — 아래 「검증」 2번.

## 검증

```bash
# 1. 빌드 (Testcontainers → Docker Desktop 필요)
./gradlew :apps:ems-service:build

# 2. 스펙 실물 — 스키마 이름 유일성 + 공통 에러 주입
./gradlew :apps:ems-service:bootRun
curl -s localhost:8087/api/ems/v3/api-docs | jq '.components.schemas | keys'
curl -s localhost:8087/api/ems/v3/api-docs | jq '.paths["/api/ems/wnp"].get.responses | keys'
```

### 실측 결과 (2026-08-25)

**빌드** — `./gradlew :apps:ems-service:build` 성공 (통합 테스트 포함).

**엔드포인트별 응답 코드** — `wnp` 6개 전부 200을 갖는다.
```
/api/ems/wnp            [get]  → 200,400,401,500
/api/ems/wnp            [post] → 200,400,401,409,500
/api/ems/wnp/list       [post] → 200,400,401,409,500
/api/ems/wnp/list       [put]  → 200,400,401,404,409,500
/api/ems/wnp/{wnpId}    [get]  → 200,400,401,404,500
/api/ems/wnp/{wnpId}    [put]  → 200,400,401,404,500
```
서비스 코드에서 읽은 예외 경로와 1:1로 일치한다(위 「결정 1」의 표).

**스키마 이름 — 충돌 없음.** 02·03의 미결 실측이 이번에 해소됐다. 봉투 특수화(`ApiResponse*`)를 뺀 19개가
전부 도메인 접두사로 유일하다.
```
CtrlGrpAddListRequest CtrlGrpAddRequest CtrlGrpEqpReplaceRequest CtrlGrpMemberReplaceResponse
CtrlGrpMemberResponse CtrlGrpModifyItemRequest CtrlGrpModifyListRequest CtrlGrpModifyRequest
CtrlGrpResponse CtrlGrpStolenMemberResponse CtrlGrpTagReplaceRequest CtrlGrpWnpReplaceRequest
ErrorResponse WnpAddListRequest WnpAddRequest WnpModifyItemRequest WnpModifyListRequest
WnpModifyRequest WnpResponse
```
`XxxDtos` 중첩 record를 금지한 규약(`apps/CLAUDE.md`)이 실제로 값을 했다 — 접두사가 없었다면
`AddRequest`·`ModifyRequest`를 두 도메인이 다퉜을 자리다.

### 이번 측정이 드러낸 것 (2026-08-25)

**이 실측 과정에서 `ctrl` 슬라이스의 결함이 드러났다** — `@ApiResponse`를 선언한 엔드포인트에서
200이 통째로 사라져 있었다. 규약을 세운 커밋의 기준 구현체가 그 상태였다.
원인·교정·근거는 [starters 01](../../../starters/docs/01-OpenAPI-성공응답-복원.md)에 있다.
**`wnp`는 이 앱의 코드를 고치지 않고 스타터 교정만으로 정상이 됐다.**

## 다음 단계

- 리뷰를 아직 돌리지 않았다 — 이 작업은 여러 모듈(ems·master·starters)에 걸치므로 `/step` 발동 조건에 해당한다.
- 스펙 실물을 대조하는 자동 검증([starters 01](../../../starters/docs/01-OpenAPI-성공응답-복원.md) 「다음 단계」).
