# Step 12 — 인증 모드 2값 단순화

- 일자: 2026-08-14
- 상태: ✅ 완료 (컴파일·단위 테스트 통과, 컨테이너 E2E는 미실행)
- 관련: [step-10-auth-slice.md](step-10-auth-slice.md), 아키텍처 4.1 / 19장

> **후속 철회 (2026-08-14)** — 이 Step이 함께 도입했던 **사이트 프로파일 축은 제거됐다.**
> `config-repo/application-<사이트>.yml` 두 개를 삭제하고 Spring 프로파일을 환경 축(`docker`)
> 하나로 되돌렸다. 모드 결정 주체는 도입 이전과 같이 `SWTP_AUTH_MODE` 환경변수다.
> 아래 "결정"의 2·3번째 항목과 그에 딸린 변경 내역·함정·한계는 더 이상 현행이 아니며,
> 기록으로만 남긴다. 남아 있는 결정은 **2값 단순화**와 **JWKS 실패 503 분리** 둘이다.

## 발단

"auth-service를 배포하지 않는 정수장에서도 게이트웨이 필터가 정상 동작하는가"를 확인하다가
세 가지가 드러났다.

1. **`mode=external`은 실제로 동작하지 않았다.** `JwtAuthGlobalFilter`가 `typ` 클레임(`"access"`)을
   강제하는데, 이는 액세스 토큰과 ws-ticket을 구분하려고 우리가 넣는 커스텀 클레임이라
   표준 OIDC IdP의 토큰 payload에는 없다. 서명이 맞아도 전 요청 401이 된다.
   즉 문서가 말하는 "`jwks-uri` 한 줄만 바꾸면 외부 IdP로 전환된다"가 성립하지 않았다.
2. **인증 모드가 `config-repo` 밖에서 결정됐다.** `compose.yaml`이 `SPRING_PROFILES_ACTIVE: docker`를
   하드코딩하고 `SWTP_AUTH_MODE`는 아예 없어서, 정수장별로 모드를 바꾸려면 배포 산출물(compose)을
   포크해야 했다. "`swtp.auth.mode`는 config-repo가 SSOT"라는 규칙이 주석상으로만 참이었다.
3. **`mode`와 `jwks-uri`가 다른 파일로 갈라져 있었다.** `mode`는 `application.yml`,
   `jwks-uri`는 `application-docker.yml`. 한 쌍으로만 의미가 있는 두 값이 분리돼
   "검증은 켜져 있는데 공개키는 없는 곳을 가리키는" 상태가 구조적으로 성립했다.

## 결정 (2026-08-14)

> - **`SwtpAuthMode`를 `INTERNAL`/`NONE` 2값으로 줄인다.** 실제 배포 현실이 "검증함/안 함" 둘이고
>   외부 IdP 연동 대상이 없다. 쓰지 않으면서 고장나 있는 선택지를 남기면 나중에 누군가 골랐을 때
>   "설정은 있는데 전부 401"이 된다. 외부 IdP가 필요해지면 그때 클레임 규약부터 함께 설계한다.
>   — `verifies()`가 `this != NONE`이라 호출부는 한 줄도 바뀌지 않았다.
> - **모드 결정 주체를 사이트 프로파일로 옮긴다.** `config-repo/application-<사이트>.yml`이
>   `mode`와 `jwks-uri`를 **한 쌍으로** 소유한다. 두 값이 같은 파일에 있어야 3번 문제가 재발하지 않는다.
> - **프로파일 축을 둘로 명시하고 순서를 `docker,<사이트>`로 고정한다.** 환경 축(`-docker`)은
>   host/port만, 사이트 축(`-<사이트>`)은 인증 설정만 갖는다. 한 키를 두 축이 함께 건드리면
>   누가 이기는지가 활성 순서에 달리므로 금지한다.
> - **JWKS 조회 실패를 401에서 분리해 503으로 낸다.** 기존에는 서명 불일치·만료·JWKS 조회 실패가
>   모두 401 + `log.debug`였다. auth-service가 죽으면 전 API가 401이 되는데 클라이언트는
>   "토큰 만료"로 오해해 재로그인을 시도하고, 그 `/api/auth/login`도 함께 죽어 있다.
>   인프라 장애가 인증 실패로 위장되는 구조였다.
>   구분선은 `BadJwtException`(토큰이 틀림) ↔ 그 밖의 `JwtException`(검증 자체가 불가) —
>   `instanceof JwtException`으로 나누면 안 된다. Nimbus가 JWKS 조회 실패도 `JwtException`으로 감싼다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `libs/swtp-common/.../SwtpAuthMode.java` | `EXTERNAL` 제거, Javadoc을 2값 근거로 교체 |
| `config-repo/application-<사이트>.yml` (신규) | `mode` + `jwks-uri` 한 쌍 — **철회됨, 현재 없음** |
| `config-repo/application-docker.yml` | `swtp.auth` 블록 제거 (환경 축은 host/port만) |
| `config-repo/application.yml` | 3값 주석 → 2값 |
| `infrastructure/docker/compose.yaml` | `SPRING_PROFILES_ACTIVE`를 `SWTP_PROFILES`로 외부화 |
| `infrastructure/docker/.env.example` (신규) | 배포 환경변수 예시 |
| `apps/gateway/.../JwtAuthGlobalFilter.java` | `BadJwtException` → 401 / 그 외 → 503 + `WARN`, `writeError` 공통화 |
| `apps/gateway/.../JwtAuthGlobalFilterTest.java` | 스텁을 `BadJwtException`으로 정정, JWKS 실패 503 테스트 추가 |
| 주석·문서 | gateway/starter 주석, 아키텍처 4.1·19장, plan.md, 두 CLAUDE.md |

`JwtAuthGlobalFilter`의 `typ` 검증과 ticket 경로, 라우트 정의, compose의 Optional `profiles:` 블록은
**건드리지 않았다** — 검증 모드가 `internal` 하나뿐이므로 조건 분기가 불필요하고, Optional 분리 구조는 정상이다.

## 함정 기록

- **`mode=none`인 곳에 `jwks-uri`를 비워 쓰지 않는다.** `SwtpSecurityEnvironmentPostProcessor`가
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri = ${swtp.auth.jwks-uri}` 파생값을 항상 주입하므로,
  값을 비우면 플레이스홀더 해석이 깨진다. `mode=none`이면 검증기를 만들지 않아 값이 쓰이지 않으니
  로컬 기본값이 그대로 남아 있으면 된다.
- **테스트 스텁이 실패를 던지는 예외 타입이 곧 계약이다.** 기존 스텁은 서명 실패를 평범한
  `JwtException`으로 던지고 있었는데, 이는 Nimbus의 실제 동작(`BadJwtException`)과 달라
  401/503 분기를 도입하는 순간 기존 테스트가 깨졌다. 정정했다.

## 알려진 한계

1. **컨테이너 E2E 미실행** — 인증함/안함 두 경우의 실제 기동 검증은 Docker Desktop 환경에서 별도 수행해야 한다.
   확인 항목은 아래 "검증" 참조.
2. **인증 모드 미지정을 기동 시점에 잡지 못한다.** 값을 빠뜨리면 폴백(`internal`)이 조용히 적용된다.
   (철회된 사이트 프로파일 축은 compose 기본값으로 이를 완화하려 했으나 강제는 없었다.)
3. **`.env`는 커밋하지 않는다** — `.env.example`만 두었다. 실제 배포 시 정수장별로 복사해 쓴다.

## 검증

```bash
./gradlew classes testClasses                       # EXTERNAL 잔여 참조 없음 확인 (통과)
./gradlew :libs:swtp-common:test :apps:gateway:test :starters:swtp-security-starter:test   # 통과
```

컨테이너 검증(미실행):

```bash
# 설정 병합 확인
curl http://localhost:8888/application/docker    # mode + jwks-uri

# mode=none — 토큰 없이 200, 경고 배너, 위조 X-User-Id 제거
SWTP_AUTH_MODE=none docker compose -f infrastructure/docker/compose.yaml up -d --build

# mode=internal — 로그인→200, 토큰 없이 401, ws-ticket 핸드셰이크 통과
docker compose -f infrastructure/docker/compose.yaml --profile auth up -d --build

# 실패 모드 — internal에서 auth-service만 내린 뒤 보호 경로 호출 → 503 + WARN
```

> 위 `SWTP_AUTH_MODE` 전달은 compose의 `x-app-env`에 해당 변수 전달을 추가해야 실제로 동작한다.
