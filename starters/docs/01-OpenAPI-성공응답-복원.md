# starters 01 — OpenAPI 성공 응답 복원

- 일자: 2026-08-25
- 상태: ✅ 완료
- 검토 관점: 없음 — 판정표상 소환 0명. 다만 **공유 스타터 수정이라 승인 게이트를 다시 거쳤다**
- 관련: [master 05](../../apps/master-service/docs/05-API-명세-적용.md) · [ems 04](../../apps/ems-service/docs/04-수계통지점-API-명세.md) · `apps/CLAUDE.md` 「API 문서화」

## 발단

master·ems에 OpenAPI 명세를 적용하면서 **스펙을 처음으로 실제로 받아 봤다.**
`apps/master-service/docs/01`과 `apps/ems-service/docs/02·03`에 "스키마 이름 충돌 실측 미완"으로
남아 있던 항목을 처리하려던 것이었는데, 스키마 이름은 멀쩡했고 대신 다른 것이 드러났다.

**`@ApiResponse`를 선언한 모든 엔드포인트에서 200이 사라져 있었다.**

```
/api/ems/ctrl-grp/{ctrlGrpId} [get] → 400,401,404,500      ← 200이 없다
/api/ems/ctrl-grp            [get] → 200,400,401,500      ← 선언이 없는 쪽은 멀쩡하다
```

규약을 세운 커밋 `841df51`의 **기준 슬라이스(ems `ctrl`)가 이미 이 상태였다.**
스펙을 받아 확인한 적이 없어 드러나지 않았을 뿐이다.

## 결정 (2026-08-25)

### 1. 원인 — springdoc이 성공 응답을 에러 자리로 옮긴다

핸들러에 `@ApiResponse`가 하나라도 선언되면, springdoc은 반환 타입에서 만들어 둔 성공 응답을
**선언된 상태 코드 쪽으로 옮기고 200 자리를 비운다.** 거짓이 두 개 실린다.

| 실측 (교정 전) | 무엇이 거짓인가 |
|---|---|
| `404 → */* → ApiResponseTagResponse` | 404가 **성공 봉투 스키마**를 광고한다 |
| `200` 없음 | 프론트가 **정상 응답의 모양을 볼 수 없다** |

### 2. 교정 위치는 스타터다 — 앱이 아니다

두 안을 재고 스타터를 골랐다.

| 안 | 내용 | 기각/채택 사유 |
|---|---|---|
| 앱에서 명시 선언 | 핸들러마다 200을 함께 선언하고 에러에 `content` 명시 | **기각.** 약 40곳 × 4줄이 늘고, 앞으로 모든 핸들러가 규칙 둘을 기억해야 한다. 하나만 빠뜨려도 조용히 거짓이 실린다. 스타터 Javadoc이 이미 *"에러 응답을 컨트롤러마다 적으면 흩어진다"*고 같은 이유로 이 자리를 자기 책임으로 선언해 뒀다 |
| **스타터에서 일괄 교정** | 공통 에러 customizer가 옮겨 붙여진 것을 되돌린다 | **채택.** 앱 코드 추가 0줄이고, **이미 결함이 들어가 있던 ems `ctrl` 14개 파일이 함께 고쳐진다** |

부분 실험으로 확인한 사실이 선택을 갈랐다 — 에러 응답에 `content`를 명시하면 **404의 스키마는 교정되지만
200은 돌아오지 않는다.** 즉 앱에서 고치려면 `content`와 200 선언 **둘 다** 필요하다.

### 3. 판정 기준은 하나 — "에러 응답인데 본문이 ErrorResponse가 아니다"

이 플랫폼에서 에러 응답은 예외 없이 `ErrorResponse` 봉투를 쓴다. 그러므로 그렇지 않은 본문은
옮겨 붙여진 것이다. 되찾은 본문은 **2xx가 하나도 없을 때만** 200으로 복원한다 —
앱이 성공 응답을 직접 선언했다면 그쪽이 옳고 스타터가 끼어들 자리가 아니다.

본문 교정(에러 → `ErrorResponse`)은 2xx 유무와 무관하게 항상 한다.
409가 성공 봉투를 광고하는 것은 어느 경우에나 거짓이기 때문이다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `swtp-web-starter/.../SwtpOpenApiAutoConfiguration.java` | `restoreStolenSuccessResponse` 추가 · `ERROR_SCHEMA_REF` 상수 추출 · `errorContent(example)` 헬퍼로 본문 생성 일원화 |
| `swtp-web-starter/.../SwtpCommonErrorResponsesCustomizerTest.java` | 테스트 3개 추가 (7 → 10) |
| 앱 코드 | **변경 없음** — 이 교정 때문에 앱이 고칠 것은 하나도 없다 |
| `apps/CLAUDE.md` | **변경 없음** — 규약 본문이 바뀐 게 아니라 스타터가 뼈대를 하나 더 책임지게 됐을 뿐이다 |

## 함정 기록

**1. `TaskStop`이 Gradle 래퍼만 죽이고 자바 프로세스는 살려 뒀다.**
포트 8091을 이전 프로세스가 계속 쥐고 있어 **첫 실험 측정이 변경 전 코드를 가리켰다.**
"패치가 듣지 않는다"는 잘못된 결론을 낼 뻔했고, `netstat`으로 점유 PID를 확인하고서야 드러났다.
로컬 기동으로 검증할 때는 **측정 전에 포트 점유 PID가 방금 띄운 것인지 확인한다.**

**2. 복원한 200을 응답 목록 맨 앞에 놓는다.** `ApiResponses`가 `LinkedHashMap`이라 그냥 추가하면
`404, 400, 401, 500, 200` 순이 된다. 스펙 순서가 곧 swagger-ui 화면 순서이고,
소비자가 먼저 보는 것은 성공 응답이다.

**3. 첫 전체 빌드에서 `job-service` 통합 테스트가 실패했으나 이 변경과 무관했다.**
`ContainerFetchException: Can't get Docker image: timescale/timescaledb-ha:pg17`인데 그 이미지는
로컬에 있었다 — 컴포즈 스택이 뜬 채로 Testcontainers를 병렬로 돌려 Docker 데몬이 경합한 것이다.
`--rerun-tasks`로 단독 재실행하니 통과했고, 이후 전체 빌드도 통과했다.
**"빌드가 빨개졌다"를 곧바로 자기 변경 탓으로 돌리지 않는다** — 실패 메시지가 가리키는 층을 먼저 본다.

**4. 교정을 공통 에러 주입보다 먼저 한다.** 400·401·500을 붙이고 나면 어느 것이 springdoc이 옮겨 붙인
것이고 어느 것이 우리가 넣은 것인지 판정이 흐려진다.

## 알려진 한계

**1. springdoc의 동작에 의존하는 교정이다.** springdoc이 이 동작을 바꾸면 판정 기준
("에러 응답인데 본문이 `ErrorResponse`가 아니다")은 그대로 참이지만 되돌릴 대상이 사라진다 —
그때는 이 메서드가 아무 일도 하지 않게 되므로 **깨지지는 않는다.** 다만 테스트가 픽스처로
springdoc의 산출물 모양을 흉내 내고 있어, 그 모양이 바뀌면 테스트만 남고 실효는 사라진다.

**2. 스펙을 실물로 대조하는 자동 검증이 없다.** 이번 결함이 커밋되고도 드러나지 않은 이유가 이것이다.
`/v3/api-docs`를 받아 "모든 operation에 2xx가 있다"를 단언하는 통합 테스트가 있으면
같은 종류의 결함이 다시 들어올 때 빌드가 알려준다 — 「다음 단계」.

## 검증

```bash
./gradlew :starters:swtp-web-starter:test
./gradlew build
# 실물 대조
curl -s localhost:8091/api/master/v3/api-docs | jq '.paths | to_entries[] | .key as $p | .value | to_entries[] | "\($p) [\(.key)] → \(.value.responses | keys | join(","))"'
```

### 실측 결과 (2026-08-25)

**스타터 단위 테스트 — 10건 전부 통과 (신규 3건 포함)**
```
tests="10" skipped="0" failures="0" errors="0"
```

**교정 전후 대조 (master, 404를 선언한 엔드포인트)**
```
교전 전: /api/master/tag/{tagSn} [get] → 400,401,404,500
교정 후: /api/master/tag/{tagSn} [get] → 200,400,401,404,500

200: */*              → #/components/schemas/ApiResponseTagResponse
404: application/json → #/components/schemas/ErrorResponse
```

**ems `ctrl` 슬라이스 — 앱 코드를 한 줄도 고치지 않고 함께 교정됐다**
```
교정 전: /api/ems/ctrl-grp/{ctrlGrpId} [get] → 400,401,404,500
교정 후: /api/ems/ctrl-grp/{ctrlGrpId} [get] → 200,400,401,404,500
교정 후: /api/ems/ctrl-grp/list [put]        → 200,400,401,404,409,500
```

두 앱 합쳐 **49개 operation 전부가 2xx를 갖는다.**

**전체 빌드 — `./gradlew build` 성공.** 공유 스타터를 고쳤으므로 소비 앱 8종 전부를 돌렸다.
`swtp-web-starter` 10건, `gateway` 5개 클래스(`ApiDocsRouteConsistencyTest` 포함),
`master-service`·`ems-service`·`job-service` 통합 테스트 모두 통과했다.

## 다음 단계

- `/v3/api-docs`를 실제로 받아 "모든 operation에 2xx가 있다" · "4xx·5xx 본문은 `ErrorResponse`다"를
  단언하는 통합 테스트. 위 「알려진 한계」 2가 그 필요를 만들었다.
- `@SecurityRequirements` 사용처가 아직 0건이다 — auth-service의 로그인 등 공개 핸들러가
  전역 bearer 요구를 해제하지 않고 있다.
