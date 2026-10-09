# 03 — 인증 검증 기본값을 `none`으로 전환하고 정수장별 `internal` 경로를 연다

- 일자: 2026-08-25
- 상태: ✅ 완료 — 컨테이너 실측 (A)(B) 통과. 실측이 JWKS 기본값 오류를 잡아냈다(아래 「실측이 잡은 것」)
- 검토 관점: 배포·운영 · 보안 · 설정 SSOT
- 리뷰: 미실시
- 관련: `config-repo/CLAUDE.md`「규칙」, `infrastructure/CLAUDE.md`「배포 구분」, `apps/gateway/CLAUDE.md`「인증」, `apps/CLAUDE.md`「통합 테스트 표준형」, `docs/architecture/스마트정수장_리빌드_아키텍처.md` 4.1 검증 모드 규약, `docs/scaffold/steps/step-12-auth-mode-2values.md`(2값 확정의 출처)

## 발단

기 구축 지자체는 **인증·인가를 타사가 앞단(포털/SSO)에서 처리하고 끝낸다.** 타사 관문을 통과한
사용자만 swtp-platform에 도달하므로 플랫폼이 사용자를 다시 식별할 근거가 없다.
이 배포 형태가 **다수**이고, 자체 auth-service로 검증하는 `internal`이 소수다.

그런데 기본값은 `internal`이었다. 다수 정수장이 매번 예외 설정을 해야 하는 방향이다.

더 큰 문제는 그 예외 설정 자체가 **불가능했다는 것**이다.
`compose.yaml`의 `x-app-env` 앵커가 `SWTP_AUTH_MODE`/`SWTP_AUTH_JWKS_URI`를 컨테이너로
전달하지 않았다(`.env.example`이 그 사실을 적어 두고 있었다). `.env`에 무엇을 적어도 닿지 않고
전 사이트가 코드 기본값 하나로 고정됐다. 즉 **"정수장별 차이는 `.env` 한 줄"이라는 규약이
인증에 대해서만 성립하지 않고 있었다.**

부수적으로, 컨테이너의 JWKS 기본값이 `localhost:8085`라 컨테이너 네트워크에서 닿지 않았다.
**컨테이너에서 `internal`이 한 번도 성립한 적이 없다**는 뜻이다.

## 결정

1. **`x-app-env`(공통 베이스)가 두 변수를 전달한다.** 앵커가 셋으로 갈라져 있지만
   (`x-platform-app-env`·`x-micro-app-env`가 각각 `<<: *app-env`), 인증 모드는 게이트웨이와
   업무 서비스가 **같은 값을 같은 뜻으로** 읽어야 하는 값이라 공통 베이스에 둔다.
   갈라진 두 앵커에 각각 적으면 그 순간 어긋날 수 있는 구조가 생긴다.
   `SWTP_DB_HOST`가 `x-micro-app-env`에만 있는 것과 정확히 반대 경우다.
2. **기본값을 `none`으로 뒤집는다.** 다섯 지점을 함께 바꾼다(아래 「바꾼 곳」).
3. **`SWTP_AUTH_JWKS_URI`의 compose 기본값은 auth-service 컨테이너 주소로 둔다.**
   `--profile auth` 구성이면 `.env`에 `SWTP_AUTH_MODE=internal` **한 줄**이면 동작한다.
4. **통합 테스트는 `swtp.auth.mode=internal`을 명시한다.** 운영 기본값과 테스트 기준값을 분리한다.

### 감수한 것 — 실패 방향이 fail-closed에서 fail-open으로 뒤집힌다

지금까지는 설정을 빠뜨리거나 오타를 내면 **전부 401이 나서 즉시 드러났다.**
이제 같은 실수가 **"플랫폼 전체가 무인증으로 열린 채 정상 동작"** 으로 나타난다.

이 위험을 알고도 택한 이유는 기본값이 배포 현실을 반영해야 한다는 판단이다 —
다수가 `none`인데 기본이 `internal`이면 매 배포가 예외 처리가 되고, 예외 처리가 일상이 되면
그것이 곧 검토 없이 복사되는 절차가 된다.

가시성 보완은 **새로 만들지 않고 있는 것을 유지**했다:
- 기동 시 `SwtpSecurityAutoConfiguration`·`GatewaySecurityConfiguration`의 `WARN` 배너(`####`)
- `.env.example`의 ★ 항목 — `SWTP_SITE_CODE`가 쓰는 것과 같은 표기

### 기각 — `external`(외부 IdP) 값을 되살리지 않았다

이번 요구는 "타사가 **앞단에서 끝내는**" 형태다. swtp-platform이 타사 토큰을 **검증하는**
형태가 아니므로 `none`으로 충분하다.

검증까지 넘겨받아야 하는 정수장이 나오면 그때는 값을 늘리는 것으로 해결되지 않는다 —
게이트웨이 검증 경로가 우리 커스텀 클레임 `typ`(액세스 토큰과 WebSocket 티켓 구분)을 요구하므로
표준 IdP 토큰은 전부 401이 된다. **클레임 규약 재설계가 선행되는 별건**이다(`SwtpAuthMode` 주석).

### `none`이 성립하기 위한 전제 셋

이 중 하나라도 깨지면 `none`은 더 이상 맞지 않는다.

1. **감사 컬럼(`rgstr_id`/`mdf_id`)이 전부 시스템 기본값**이 된다 — "누가 바꿨는가"가 남지 않는다.
2. **`@PreAuthorize` 등 메서드 단위 권한이 붙은 API가 생기면 403**이 된다. 인증 주체가 없다.
3. **게이트웨이가 `none`에서도 `X-User-*` 헤더를 지운다**(`JwtAuthGlobalFilter`).
   앞단이 사용자 정보를 헤더로 실어 보내도 다운스트림에 도달하지 않는다.
   "인증을 안 한다"와 "클라이언트가 자기를 관리자라고 주장할 수 있다"는 다른 문제라서 의도적이다.
   **앞단의 사용자 신원을 서비스까지 흘려야 한다면 신뢰 경계 설계가 별도로 필요하다.**

## 바꾼 곳

### 전달 경로 (선결)

| 파일 | 변경 |
|---|---|
| `infrastructure/docker/compose.yaml` | `x-app-env`에 `SWTP_AUTH_MODE`(기본 `none`) · `SWTP_AUTH_JWKS_URI`(기본 auth-service 주소) 추가, 머리 주석 갱신 |
| `infrastructure/docker/.env.example` | "여기서 결정되지 않는다" 블록 → 실제 설정 항목 2개 (★ 경고 포함) |

### 기본값 — **다섯** 지점

계획 단계에서 넷으로 셌으나 게이트웨이 자기 yml이 하나 더 있었다.
`swtp.auth.mode`만 grep하면 놓친다 — yml에서는 `mode:` 한 단어로 나타나기 때문이다.

| 파일 | 내용 |
|---|---|
| `starters/.../SwtpSecurityEnvironmentPostProcessor.java` | `${SWTP_AUTH_MODE:none}` (서비스 최저 우선순위 기본값) |
| `config-repo/application.yml` | `mode: ${SWTP_AUTH_MODE:none}` (SSOT) |
| `apps/gateway/src/main/resources/application.yml` | `mode: ${SWTP_AUTH_MODE:none}` (게이트웨이 단독 기동 기본값) |
| `starters/.../SwtpAuthProperties.java` | 필드 초기값 `NONE` |
| `apps/gateway/.../GatewayAuthProperties.java` | 필드 초기값 `NONE` |

필드 초기값까지 바꾸는 이유: config-server가 안 붙은 상태에서는 그 값이 살아난다.
앞의 셋만 바꾸면 로컬 단독 기동과 컨테이너 동작이 갈린다.

`SwtpAuthMode` enum은 값을 늘리거나 줄이지 않았다 — "기본값" 표기만 `INTERNAL`에서 `NONE`으로 옮겼다.
`swtp.auth.jwks-uri`의 앱 쪽 기본값도 그대로 뒀다: `mode=none`이어도 비우면
파생 키 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`가 비어 `JwtDecoder` 빈 생성이 깨진다.

### `config-repo/application-dev.yml`의 리터럴 `none` 제거

공통 기본이 `none`이 되면서 중복이 됐고, 이 파일이
"`swtp.auth.mode`는 환경변수가 결정한다 — yml 파일로 되돌려 놓지 않는다"(`config-repo/CLAUDE.md`)를
어기는 유일한 지점이었다. 이 삭제로 **미결 항목 셋이 함께 닫혔다**:

- `docs/scaffold/steps/step-18` 한계 #3 — dev.yml이 규칙과 충돌
- `docs/scaffold/steps/step-19` 미착수 #2 — 같은 뿌리
- `apps/master-service/CLAUDE.md`의 함정 — `spring.profiles.active: dev`가 박혀 있어
  **로컬 `bootRun`만 인증이 꺼지던** 갈라짐. 이제 로컬·컨테이너 기본값이 같다.

`spring.profiles.active: dev` 자체는 손대지 않았다 — 인증 관점의 갈라짐이 사라졌으므로 급하지 않고,
개발 편의로 의도된 것일 수 있어 임의로 지우지 않는다(step-19의 판단을 유지한다).

### 통합 테스트 — 이 변경의 최대 부작용

**6개 앱의 "토큰 없으면 401" 방어 테스트가 조용히 200으로 통과하게 된다.**
방어가 사라지는 것이 아니라 **방어를 검증하던 테스트가 무력해지는** 쪽이라 더 위험하다.
그 테스트들은 `spring.config.import=`로 config-server를 끊으므로 값이 스타터 fallback에서 오기 때문이다.

`AbstractIntegrationTest` 5개(master·telemetry·job·auth·ems)의 `@SpringBootTest(properties)`에
`swtp.auth.mode=internal`을 넣었다. 슬라이스 테스트는 전부 이 클래스를 상속하므로 한 곳씩이면 된다.
`apps/CLAUDE.md`의 "반드시 넣는다 — 두 값"을 **세 값**으로 갱신했다.

스타터 단위 테스트는 양방향을 잠갔다 — `defaultsToNoneMode`(기본값) +
`internalModeAddsResourceServer`(신규, 반대 방향). 한쪽만 두면 기본값을 되돌릴 때 그물이 되지 못한다.

게이트웨이 `JwtAuthGlobalFilterTest`는 영향 없다 — 필터를 `SwtpAuthMode`를 인자로 직접 생성한다.

## 함정 — `SWTP_AUTH_JWKS_URI`를 빈 값으로 두면 기동이 깨진다

계획 초안은 compose 기본값을 빈 문자열(`${SWTP_AUTH_JWKS_URI:-}`)로 두려 했다. 착수 중 기각했다.

**Spring 플레이스홀더의 기본값은 "부재"에만 적용되고 "빈 값"에는 적용되지 않는다.**
compose가 빈 문자열을 넘기면 환경변수는 *존재하되 빈 값*이라
`${SWTP_AUTH_JWKS_URI:http://localhost:8085/...}`의 기본값이 발동하지 않고 `""`가 된다.
그러면 파생 키 `jwk-set-uri`도 비고, Boot의 `JwtDecoder` 자동구성이 `@ConditionalOnProperty`로
"존재함"을 판정해 `NimbusJwtDecoder.withJwkSetUri("")`를 시도하다 빈 생성에서 죽는다.
`mode=none`이어도 그 빈은 우리 필터체인과 별개로 만들어진다.

`SwtpSecurityEnvironmentPostProcessor`의 주석이 이미 예고한 함정이다 —
"값을 비우면 이 키를 참조하는 Boot 자동구성 쪽에서 플레이스홀더 해석이 깨진다".

`.env`에 `SWTP_AUTH_JWKS_URI=`(빈 값)를 두는 것은 **안전하다** — compose의 `:-` 형태가
빈 값을 미지정으로 취급해 기본값으로 치환하기 때문이다. 위험한 것은 compose가 컨테이너로
빈 값을 내보내는 경우다.

## 검증

### 실행함 ✅

```bash
./gradlew build          # BUILD SUCCESSFUL (3m22s)
```

401 방어 테스트 **13건이 이번 빌드에서 재실행되어 전부 통과**했다(실패 0). up-to-date로 건너뛰지
않았음을 `build/test-results/test/TEST-*.xml`의 timestamp로 확인했다 — 이 확인이 필요한 이유는,
이 변경의 위험이 "테스트가 깨지는 것"이 아니라 **"테스트가 조용히 무의미해지는 것"** 이기 때문이다.
깨졌으면 빌드가 알려주지만, 안 돈 테스트는 아무것도 알려주지 않는다.

스타터 단위 테스트는 양방향(`defaultsToNoneMode`·`internalModeAddsResourceServer`) 모두 통과.

전달 경로는 컨테이너를 띄우지 않고 `config` 렌더링으로 확인했다 — Spring 앱 **10종 전부**에
전개된다(config-server 제외: 자기가 서빙하는 쪽이라 이 값을 읽지 않는다. ai-service는 Python).

```bash
docker compose -f compose.yaml config | grep SWTP_AUTH
#   → SWTP_AUTH_MODE: none / JWKS: http://auth-service:8085/...
SWTP_AUTH_MODE=internal docker compose -f compose.yaml config | grep SWTP_AUTH
#   → SWTP_AUTH_MODE: internal (JWKS 기본값 유지 — .env 한 줄로 켜지는 것이 확인됨)
SWTP_AUTH_MODE=internal SWTP_AUTH_JWKS_URI=https://idp.example/jwks.json ... config
#   → 두 값 모두 치환됨 (발급처가 밖에 있는 경우)
```

### 컨테이너 실측 — 실행함 ✅

**컨테이너 실측 — 두 형태를 모두 띄워야 검증이다.** 목적이 "사이트마다 다르게"이므로
한쪽만 봐서는 이번 작업이 성립했는지 알 수 없다. `config` 렌더링이 증명하는 것은
**compose가 값을 컨테이너까지 넘긴다**는 데까지이고, 그 값을 앱이 실제로 그렇게 해석하는지는
띄워 봐야 안다 — 특히 config-server가 내려주는 `${SWTP_AUTH_MODE:none}`이 서버가 아니라
**클라이언트 쪽에서** 풀린다는 전제가 여기서 실증된다.

```bash
./gradlew bootJar

# (A) .env에 인증 설정 없음 → none으로 떠야 한다
docker compose -f infrastructure/docker/compose.yaml up -d --build
docker compose -f infrastructure/docker/compose.yaml logs master-service | grep -A3 '####'
#   → "swtp.auth.mode=none" 경고 배너
curl -i http://localhost:8080/api/master/...        # 토큰 없이 200

# (B) .env에 SWTP_AUTH_MODE=internal (JWKS는 비워 둔 채로)
docker compose -f infrastructure/docker/compose.yaml --profile auth up -d --force-recreate
docker compose -f infrastructure/docker/compose.yaml logs gateway | grep '인증 모드'
#   → "게이트웨이 인증 모드=INTERNAL — JWKS http://auth-service:8085/..."
curl -i http://localhost:8080/api/master/...        # 토큰 없이 401 + COMMON-401
#   로그인해 받은 토큰을 Authorization: Bearer로 → 200
```

**(B)가 진짜 회귀 지점이다.** 컨테이너에서 `internal`이 성립하는 첫 사례이기 때문이다.

실측 결과 (2026-08-25):

| # | 확인 | 결과 |
|---|---|---|
| A-1 | `.env` 없이 기동 → 게이트웨이·전 서비스 경고 배너 | ✅ `swtp.auth.mode=none` 배너 |
| A-2 | 토큰 없이 `GET /api/ems/ctrl-grp` | ✅ **200** |
| B-1 | `.env`에 `SWTP_AUTH_MODE=internal` **한 줄**만 | ✅ 게이트웨이·서비스 모두 `INTERNAL`로 기동 |
| B-2 | 토큰 없이 호출 | ✅ **401** + `COMMON-401` |
| B-3 | `admin` 로그인 → 토큰 발급 | ✅ 200 |
| B-4 | 발급 토큰으로 호출 | ✅ **200** `{"code":"SUCCESS","data":[]}` |
| B-5 | 서명이 맞지 않는 토큰 | ✅ 401 |
| B-6 | `actuator/health` (공개 경로) | ✅ 200 |

## 실측이 잡은 것 — JWKS 기본값이 틀렸다

`SWTP_AUTH_JWKS_URI`의 compose 기본값을 처음에 `http://auth-service:8085/...`로 넣었다.
**컨테이너에서 이 주소는 닿지 않는다.** (B-4가 `COMMON-503`으로 떨어져 드러났다.)

원인은 최근 도입된 `micro` 프로파일이다 — `config-repo/application-micro.yml`이
`server.port: 0`(랜덤 포트)을 주므로 auth-service의 컨테이너 안 실제 포트는 매 기동 달라진다
(실측 당시 36433). `apps/CLAUDE.md`의 포트 표에 있는 8085는 이제 **Eureka에 등록되는 논리적 구분**이지
컨테이너가 실제로 여는 포트가 아니다.

컨테이너끼리는 Eureka(`lb://`)로 찾지만 **JWKS 조회는 그 경로를 쓸 수 없다** —
`NimbusReactiveJwtDecoder`가 평범한 HTTP URI로 가져오기 때문이다. 그래서 **게이트웨이를 경유한다**:

```
http://gateway:8080/api/auth/.well-known/jwks.json
```

게이트웨이가 Eureka로 auth-service를 찾아 넘겨주고, 이 경로는 `permit-all-paths`에 있어
토큰 없이 열린다. **`compose-dev.yaml`이 이미 이 값을 쓰고 있었다** — 계획 단계에서 `compose.yaml`만
보고 dev 쪽에 있던 정답을 지나쳤다.

**이 오류는 단위·통합 테스트로 잡히지 않는다.** 컨테이너 네트워크에서만 성립·불성립이 갈리는 값이라
`./gradlew build`는 초록불이었다. (B) 실측이 유일한 그물이었고, 이것이 그 절차를 ✅ 조건으로 둔 이유다.

## 되돌리는 법

기본값만 되돌리려면 「바꾼 곳 — 기본값」 표의 다섯 지점을 `internal`로 되돌리고,
통합 테스트의 `swtp.auth.mode=internal`과 `apps/CLAUDE.md`의 "세 값"을 함께 되돌린다.
**전달 경로(1단계)는 되돌리지 않는다** — 그건 기본값 방향과 무관하게 있어야 하는 것이고,
없으면 정수장별 지정 자체가 불가능해진다.

## 남은 것

- **`apps/CLAUDE.md`의 포트 표가 `micro` 프로파일 도입 이후의 사실과 어긋난다.** 업무 서비스의
  8081~8088은 이제 컨테이너가 여는 포트가 아니다(`server.port: 0`). 이번 JWKS 오류의 뿌리가
  그 표를 사실로 읽은 것이었다. 표의 의미를 다시 정의할지는 별건이라 손대지 않았다.
- `infrastructure/docker/compose-dev.yaml`이 `SWTP_AUTH_MODE: none`을 리터럴로 갖고 있다.
  이제 기본값과 같아 무해하고, 개발 전용 compose가 명시적으로 고정하는 것은 규약 위반이 아니라
  그대로 뒀다. 다만 기본값을 다시 뒤집을 때 함께 볼 자리다.
- `apps/master-service`의 `spring.profiles.active: dev` — 인증 갈라짐은 해소됐으나 고정 자체는 남았다
