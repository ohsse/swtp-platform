# Step 10 — 인증/인가 슬라이스 (감사 컬럼 규약 재정의 포함)

- 일자: 2026-08-14
- 상태: ✅ 완료 (전체 스택 기동 + 게이트웨이 경유 E2E 검증까지 완료)
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 10

> **이후 변경 (Step 12)**: `swtp.auth.mode`의 `external` 값이 제거되어 `internal`/`none` 2값이 되었고,
> 모드 결정 주체는 `SWTP_AUTH_MODE` 환경변수다.
> 아래 본문의 "외부 IdP 대체 가능" 서술은 그 시점의 전제다 — [step-12-auth-mode-2values.md](step-12-auth-mode-2values.md) 참조.

> **이후 변경 (Step 14) — 아래 감사 컬럼 결정 중 한 항목이 번복되었다**:
> `mdf_dttm`/`mdf_id`는 **nullable이 아니라 NOT NULL**이며, 등록 시점에 등록값과 동일하게 함께 채워진다
> (`modifyOnCreate` 기본값 `true`). 따라서 본문의 "`mdf_*`는 nullable, `modifyOnCreate=false`" 항목(17-23행),
> 구조도의 `(nullable)`·`modifyOnCreate = false` 표기(46-51행), 검증 결과의 "등록 직후 mdf_* = NULL"(169행)은
> 모두 그 시점의 기록이다 — [step-14-audit-mdf-not-null.md](step-14-audit-mdf-not-null.md) 참조.
> **2단 규약 자체(등록 전용 → `BaseCreatedEntity` / 등록+수정 → `BaseEntity`)와 `AuditorProvider` SPI는 그대로 유효하다.**

## 목표

Phase 9에서 남은 두 주제 중 **JWT 책임 분배와 게이트웨이 인증 필터**를 구현한다.
같은 작업에서 **플랫폼 감사 컬럼 규약**을 사내 표준(`rgstr_*`/`mdf_*`)으로 재정의한다 —
auth 스키마가 그 규약이 처음 적용되는 실도메인이므로, 순서를 뒤집으면 곧바로 마이그레이션 부채가 된다.

> 결정 (2026-08-14):
> - **감사 컬럼은 2단 규약** — 등록 전용 테이블과 등록+수정 테이블을 `BaseCreatedEntity` / `BaseEntity`로 나눈다.
>   수정하지 않는 테이블에 `mdf_*`를 형식적으로 붙이면 "항상 NULL인 컬럼"이 스키마 전반에 남아,
>   나중에 그 NULL이 *수정 안 됨*인지 *컬럼이 무의미함*인지 구분할 수 없게 된다.
> - **`mdf_*`는 nullable, `modifyOnCreate=false`** — `mdf_dttm IS NULL`이 "한 번도 수정되지 않음"을
>   그대로 의미하게 한다. 대안(등록 시 등록값을 복사해 채우고 NOT NULL) 기각: 정렬은 `COALESCE`로 되지만
>   한 번 복사해 넣으면 "수정 이력 없음"이라는 정보는 복원할 수 없다.
> - **발급 = auth-service / 검증 = security-starter(서블릿) + 게이트웨이(반응형)** — RS256 비대칭.
>   아키텍처 4.1이 auth-service를 Optional·외부 IdP 대체 가능으로 규정하므로 검증이 발급처에 종속되면 안 된다.
>   대칭키 기각: 전 서비스가 서명키를 공유해야 해, 서비스 한 곳이 뚫리면 토큰 위조가 가능해진다.
> - **게이트웨이는 Spring Security를 얹지 않고 GlobalFilter로 검증** — 게이트웨이는 자기 API가 없는 순수 라우터라
>   인가 규칙이 "경로 화이트리스트 + 토큰 검증"뿐이다. SecurityWebFilterChain을 얹으면 인증 판단 지점이
>   둘로 갈려 화이트리스트를 양쪽에 중복 관리해야 한다. 어려운 부분(서명·JWKS 캐싱·kid 선택)은
>   `ReactiveJwtDecoder`에 그대로 위임한다.
> - **다중 방어** — 게이트웨이가 통과시켰다고 서비스가 신뢰하지 않는다. 각 서비스가 토큰을 재검증한다.
>   `X-User-*` 헤더는 인증 근거가 아니라 로깅·감사용 부가 정보다.
> - **Realtime 인증은 단기 티켓 방식** — 브라우저의 `EventSource`/`WebSocket`은 Authorization 헤더를
>   붙일 수 없다. 액세스 토큰을 쿼리 파라미터로 넘기는 대안은 접근 로그·Referer에 장수명 토큰을 남기므로 기각.
>   30초짜리 `typ=ws-ticket` 토큰을 따로 발급한다 (`plan.md:35`의 미결 항목 해소).

**영구 산출물**: 감사 컬럼 규약 2단 분리, `AuditorProvider` SPI, security-starter 실구현 + 테스트 픽스처,
auth-service 발급 슬라이스, 게이트웨이 인증 관문 + CORS 중앙화.

## 실행 내용

### 1. 감사 컬럼 규약 재정의

컬럼 표준: `rgstr_dttm`(TIMESTAMP) / `rgstr_id`(VARCHAR 50) / `mdf_dttm`(TIMESTAMP) / `mdf_id`(VARCHAR 50).

```
starters/swtp-persistence-starter/
├── BaseCreatedEntity          # 등록 전용 — rgstr_dttm, rgstr_id (updatable=false)
├── BaseEntity extends ↑       # 등록+수정 — + mdf_dttm, mdf_id (nullable)
├── SwtpAuditorAware           # 플랫폼 유일의 AuditorAware
└── SwtpJpaAuditingAutoConfiguration  # @EnableJpaAuditing(modifyOnCreate = false)

libs/swtp-common/
└── com.mo.swtp.common.audit.AuditorProvider   # "현재 사용자는 누구인가" SPI
```

**`AuditorAware`를 스타터끼리 경쟁시키지 않는 이유**: Spring Data는 `AuditingHandler` 빈 정의에
`AUTOWIRE_BY_TYPE`을 건다(`AuditingBeanDefinitionRegistrarSupport`). 즉 컨텍스트에 `AuditorAware`가
둘이면 기동이 깨지고, `@ConditionalOnMissingBean`으로 경쟁시키면 결과가 자동구성 평가 순서에 좌우된다.
그래서 `AuditorAware`는 영속성 스타터가 독점하고, "현재 사용자"만 `AuditorProvider`로 **런타임 위임**한다.
보안 스타터가 그 구현체를 등록하고, 없으면 시스템 기본값(`SYSTEM`)이 쓰인다 —
스케줄러·Kafka 컨슈머처럼 인증 컨텍스트가 없는 경로에서도 `rgstr_id`(NOT NULL)가 반드시 채워져야 하기 때문이다.

`sample_item`(master)과 auth 스키마 7개 테이블을 새 규약으로 정합화했다.
`SampleItem`의 `@PrePersist`/`@PreUpdate` 수기 구현은 삭제하고 스타터 상속으로 대체했다.

### 2. `swtp-security-starter` 실구현 (서블릿, 검증 전담)

| 파일 | 역할 |
|---|---|
| `SwtpSecurityAutoConfiguration` | SecurityFilterChain 기본값, `JwtAuthenticationConverter`, `@EnableMethodSecurity` |
| `SwtpSecurityProperties` | `permit-all-paths`(플랫폼 기본) + `public-paths`(앱 추가분) **합집합** |
| `SwtpPrincipal` / `@CurrentUser` / `CurrentUserArgumentResolver` | 컨트롤러가 `Jwt` 원본을 보지 않게 하는 방화벽 |
| `SwtpSecurityContext` | 서비스 계층용 SecurityContext 유틸 (아키텍처 15.1) |
| `SwtpSecurityErrorResponder` | 401/403을 `{code, data}` 공통 봉투로 — 필터 단계 오류는 `@RestControllerAdvice`를 타지 않는다 |
| `SecurityContextAuditorProvider` | 감사 컬럼에 로그인 사용자 공급 |
| `SwtpSecurityEnvironmentPostProcessor` | `swtp.auth.jwks-uri` SSOT → 표준 키로 전개 |
| `testFixtures/SwtpTestJwt` | 각 서비스 통합 테스트용 토큰 발급기 (JVM 1회 RSA 키쌍 생성 — 리포지토리에 개인키를 두지 않는다) |

**예외 경로를 두 목록으로 나눈 이유**: 리스트형 프로퍼티는 병합이 아니라 **치환**이다.
하나로 두면 앱이 자기 경로 하나를 추가하려고 재정의하는 순간 actuator·API 문서 경로가 통째로 사라진다.

**자동구성 순서**: Boot 4.1은 기본 보안 체인을 세 곳(`ServletWebSecurityAutoConfiguration`,
`OAuth2ResourceServerWebSecurityAutoConfiguration`, `ManagementWebSecurityAutoConfiguration`)에서 등록하고
셋 다 `@ConditionalOnDefaultWebSecurity`로 동작한다. `before`에 셋을 모두 적어야 우리 체인이 이긴다 —
하나라도 빠뜨리면 permitAll 규칙이 없는 Boot 기본 체인이 살아남아 actuator까지 401이 된다.

### 3. `auth-service` 발급 구현

Flyway `V1__auth_schema.sql` — users / roles / user_roles / permissions / role_permissions /
refresh_tokens / jwt_signing_keys. `V2__default_admin.sql`로 `admin` 계정과 기본 역할 3종을 시드한다.

- **서명키는 DB 보관**: 재기동해도 발급한 토큰이 살아 있어야 하고, 인스턴스를 여러 개 띄워도 같은 키로
  서명해야 한다. Redis 미사용 원칙(15) 아래 공유 상태를 둘 곳은 PostgreSQL뿐이다(원칙 16).
  최초 기동 동시성은 `ux_jwt_signing_keys_active` **부분 유니크 인덱스**로 DB가 막는다 —
  한쪽만 INSERT에 성공하고 나머지는 그 키를 다시 읽는다.
- **리프레시 토큰은 원문을 저장하지 않는다**: SHA-256 해시만 둔다. DB가 유출돼도 재사용이 불가능하다.
- **회전 + 재사용 탐지**: 매 재발급마다 기존 토큰을 폐기한다. 이미 폐기된 토큰이 다시 제출되면
  탈취로 보고 해당 사용자의 모든 토큰을 끊는다.
- **사용자 열거 방지**: 계정이 없어도 더미 해시로 BCrypt 비교 비용을 치른다. 없는 계정과 틀린 비밀번호의
  응답이 내용·소요시간 모두 구분 불가능해야 한다.

엔드포인트: `POST /api/auth/{login,refresh,logout,ws-ticket}`, `GET /api/auth/me`,
`GET /api/auth/.well-known/jwks.json`(RFC 7517 — 여기만 공통 봉투를 쓰지 않는다).

### 4. 게이트웨이 인증 관문 + CORS 중앙화

`JwtAuthGlobalFilter` (order = `HIGHEST_PRECEDENCE + 20` — 접근 로그 다음이라 401로 끊긴 요청도 로그에 남는다):

1. 클라이언트가 보낸 `X-User-*`를 **인증 여부와 무관하게 항상 제거** — 화이트리스트 경로에서만 빠뜨려도 그 경로가 위조 우회로가 된다
2. OPTIONS(CORS 사전 요청)와 화이트리스트 경로는 통과
3. `Authorization: Bearer` → `typ=access`만 허용
4. `?ticket=` → **실시간 경로에서만**, `typ=ws-ticket`만 허용
5. 검증 성공 시 `X-User-Id` / `X-User-Roles` 주입

토큰 용도를 양방향으로 잠근 결과, 유출된 티켓으로 일반 API를 호출하는 것과 액세스 토큰을 쿼리 파라미터로
흘리는 것이 모두 차단된다.

게이트웨이는 `spring-boot-starter-security`가 아니라 `spring-security-oauth2-jose`만 의존한다 —
전자를 넣으면 `spring-security-config`가 딸려와 Boot의 반응형 시큐리티 자동구성이 켜지고,
기본 체인이 모든 요청에 인증을 요구한다. 의존성 트리에 `spring-security-config`/`-web`이 없음을 확인했다.

CORS는 `globalcors`로 중앙화하고, 서비스 측(security-starter)은 CORS를 **끈다** —
양쪽에서 붙이면 헤더가 중복돼 브라우저가 거부한다.

### 5. 서비스 3종에 다중 방어 적용

master / telemetry / job에 security-starter를 추가하고, 통합 테스트는 픽스처로 토큰을 실어 보낸다.
`AbstractIntegrationTest`에 인터셉터 기반 기본 인증 + `callWithoutToken()`(게이트웨이 우회 재현)을 넣어
각 슬라이스 테스트가 401 시나리오를 한 줄로 검증하게 했다.

## 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 탈취 대응이 조용히 취소됨 (통합 테스트가 검출) | `rotate()`가 전체 폐기 직후 401을 던져 **같은 트랜잭션이 롤백**되며 폐기까지 되돌아감 | `TokenBreachService`를 별도 빈으로 분리하고 `REQUIRES_NEW`로 커밋. 자기 호출은 프록시를 우회하므로 빈 분리가 필수 |
| `roles` 매핑 테스트 실패 | Security 7이 인증 요소 개념을 도입해 `FactorGrantedAuthority[FACTOR_BEARER]`를 자동 추가 | `containsExactly` → 역할 권한만 골라 `contains`로 검증 (프레임워크 업그레이드에 깨지지 않게) |
| `spring.factories` 검증 테스트가 `FileSystemNotFoundException` | `getResource()`는 클래스패스 **첫 매치**만 반환 — 스프링 자신의 `spring.factories`(JAR 내부)가 먼저 걸림 | `getResources()`로 전부 열거하고 URL 스트림으로 읽도록 변경 (persistence-starter의 동일 잠재 결함도 함께 수정) |
| 서명 변조 테스트가 통과해 버림 | 서명부 **마지막 글자**만 바꾸면 base64url 끝자리의 잉여 비트라 디코딩 결과가 동일할 수 있음 | 다른 토큰의 서명을 갖다 붙여 확실한 불일치를 만든다 |
| Boot 4.1 클래스 위치 변경 | `OAuth2ResourceServerAutoConfiguration`이 `...autoconfigure.servlet` → `...autoconfigure`로 이동 | JAR의 `AutoConfiguration.imports`를 직접 확인해 정정 |
| `LocalServerPort` 패키지 오인 | Boot 4에서도 `org.springframework.boot.test.web.server`에 있다 | JAR 검색으로 확인 후 정정 |
| auth-service 컨테이너 기동 순서 | Phase 10에서 DB 의존이 새로 생겼는데 `depends_on`은 config-server만 | `<<: [*depends-config, *depends-db]`로 보강 |
| master-service 기동 실패 — Flyway checksum mismatch | 감사 컬럼 규약 변경으로 **이미 적용된 `V1__sample_item.sql`이 수정**됨 | 마이그레이션 파일이 명시한 개발 단계 리셋 절차를 따름. 다만 Kafka/Loki 데이터까지 날리는 `down -v` 대신 `master.sample_item` + `master.flyway_schema_history`만 DROP. **운영 반입 후에는 이 방식이 불가능하므로**, 적용된 마이그레이션은 수정하지 않고 새 버전을 추가해야 한다 |
| 앱 포트 게시 거부 (Windows 포트 예약) | 호스트 환경 문제 — 코드/설정과 무관 | `compose.no-publish.yaml` override로 **게시만 제거**하고 docker 네트워크 안에서 검증. 컨테이너 내부 포트는 예약의 영향을 받지 않는다 |
| 접근 로그 결함 2건 (Phase 9 잔존) | 게이트웨이를 실제로 기동하면서 드러남 | [step-09 "문제와 해결(후속)"](step-09-observability-persistence.md) 참조 — 오버로드 오선택으로 인한 로그 소실, 응답 헤더 중복 |

## 검증 결과

```text
gradlew build                                          ✅ 전 모듈 그린 (통합 테스트 포함)
docker compose --profile full config --quiet           ✅ 파싱 통과

── auth-service 통합 테스트 (Testcontainers PG, 9건) ────────────────────
로그인 → access/refresh 발급, /me 조회                  ✅
토큰 없이 /me → 401 + COMMON-401                        ✅
서명 불일치 토큰 → 401                                  ✅
잘못된 비밀번호 / 없는 계정 → 401, 응답 구분 불가        ✅ (사용자 열거 차단)
JWKS 공개 — kty/kid 노출, 개인키 성분(d, p) 없음        ✅
리프레시 회전 + 재사용 탐지 → 사용자 전체 토큰 폐기      ✅
로그아웃 후 해당 리프레시 토큰 사용 불가                 ✅
ws-ticket — 미인증 401, 발급 토큰의 typ=ws-ticket        ✅
감사 컬럼 — 등록 시 mdf_* NULL, 폐기 시 mdf_* 채워짐,
            rgstr_dttm 불변                              ✅

── master-service (다중 방어 + 감사 규약) ────────────────────────────
토큰 없는 직접 호출 → 401 + COMMON-401                  ✅ (게이트웨이 우회 방어)
actuator/health → 200 (토큰 불필요)                     ✅
rgstr_id = 토큰 sub("it-user")                          ✅ (AuditorProvider 배선 증명)
등록 직후 mdf_dttm/mdf_id = NULL                        ✅ (modifyOnCreate=false)
수정 후 mdf_id = "it-user", rgstr_* 불변                ✅
telemetry/job 동일 시나리오                              ✅

── gateway (단위 10건) ───────────────────────────────────────────────
화이트리스트 경로는 토큰 없이 통과                       ✅
토큰 없음/서명 불일치 → 401 + COMMON-401 봉투            ✅
유효 토큰 → X-User-Id / X-User-Roles 주입                ✅
클라이언트 위조 헤더 → 게이트웨이 값으로 덮어씀           ✅ (스푸핑 차단)
화이트리스트 경로에서도 위조 헤더 제거                    ✅
ws-ticket을 Authorization으로 사용 → 401                 ✅
액세스 토큰을 ?ticket=으로 사용 → 401                    ✅
?ticket=은 /api/realtime/** 에서만 유효                  ✅
OPTIONS 사전 요청 통과                                   ✅
필터 순서 = 접근 로그 다음                               ✅

── security-starter (자동구성 6건) ──────────────────────────────────
SecurityFilterChain + AuditorProvider 등록               ✅
비웹 애플리케이션 미오염                                 ✅
앱 정의 체인이 스타터 기본 체인을 대체                    ✅
public-paths가 기본값을 치환하지 않고 합집합              ✅
roles 클레임 → ROLE_ 접두 권한 매핑                       ✅

── 전체 스택 E2E (컨테이너 기동, docker 네트워크 내부 호출) ──────────
전 앱 컨테이너 healthy                                   ✅
POST /api/auth/login → access + refresh 발급             ✅
GET  /api/auth/.well-known/jwks.json (게이트웨이 경유)    ✅ kty/kid/n/e만 노출
토큰 없이 GET /api/master/... → 401 + COMMON-401         ✅
유효 토큰 → 201, rgstrId="admin"                         ✅ 토큰 sub가 감사 컬럼까지 전달
X-User-Id 위조 전송 → rgstrId는 여전히 "admin"           ✅ 위조 헤더가 감사 기록을 오염시키지 못함
게이트웨이 우회 직접 호출(master-service:8081) → 401      ✅ 다중 방어
actuator/health — gateway 200, master 200 (토큰 없이)     ✅
CORS 사전 요청 → Allow-Origin/Methods/Expose-Headers      ✅
ws-ticket을 Authorization으로 → 401                       ✅
액세스 토큰을 ?ticket= 으로 → 401                         ✅
서명 위조 토큰 → 401                                      ✅
```

컨테이너 검증에 쓴 명령:

```bash
gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml \
               -f infrastructure/docker/compose.no-publish.yaml \
               --profile auth up -d --build
docker run --rm --network swtp-platform_default curlimages/curl:8.11.1 \
       -s http://gateway:8080/api/auth/.well-known/jwks.json
```

## 결정 사항 및 알려진 한계

**결정**

1. 감사 컬럼 타입은 사내 표준을 따라 `TIMESTAMP`(시간대 없음) + `LocalDateTime`이다.
   기존 프로젝트 관례였던 `TIMESTAMPTZ`/`Instant`는 **측정·이벤트 시각**에 계속 쓴다 —
   감사 컬럼은 "누가 언제 입력했는가"라는 운영 기록이고, 측정 시각은 시계열 데이터의 값이라 성격이 다르다.
2. 액세스 토큰은 만료 전 폐기가 불가능하다(서명 검증 방식). 즉시 차단이 필요한 상황은
   짧은 유효기간(30분) + 리프레시 토큰 폐기로 대응한다. 폐기 목록(blacklist)은 매 요청 DB 조회를
   부르므로 채택하지 않았다.
3. `roles`·`permissions` 테이블은 DDL만 두고 엔티티/관리 API는 만들지 않았다.
   Phase 10 판정 기준은 인증(발급·검증)이고, 권한 기반 인가는 별도 슬라이스다.
   스키마를 먼저 확정해 두어 그때 테이블 추가 마이그레이션 없이 사용만 하면 되게 했다.

**알려진 한계**

1. **호스트 포트 게시는 여전히 불가** — Windows가 TCP 7981–8380을 동적 예약해 앱 포트(8080~8088)
   게시가 거부된다(`netsh interface ipv4 show excludedportrange protocol=tcp`). 코드 문제가 아니다.
   현재는 `compose.no-publish.yaml` override로 게시만 빼고 docker 네트워크 안에서 검증한다.
   호스트 브라우저에서 API를 직접 호출하려면 관리자 권한으로
   `net stop winnat && net start winnat` 또는 `netsh int ipv4 set dynamic tcp start=49152 num=16384`
   후 재부팅이 필요하다.
2. **적용된 Flyway 마이그레이션 수정은 개발 단계에서만 가능하다** — 이번에 `V1__sample_item.sql`을
   감사 규약에 맞춰 고치면서 checksum mismatch로 기동이 막혔고, 해당 스키마를 리셋해 해소했다.
   운영 반입 후에는 기존 파일을 고치지 않고 `V2`, `V3`로 컬럼 변경 마이그레이션을 추가해야 한다.
3. **서명 개인키 평문 보관** — DB 읽기 권한이 곧 토큰 위조 권한이다. 운영 반입 시 KMS/시크릿 매니저로
   옮기고 `jwt_signing_keys`에는 공개키·kid만 남기는 것이 맞다. `config-repo`의 평문 DB 비밀번호,
   Grafana `admin/admin`과 함께 **운영 반입 전 필수 조치** 목록이다.
4. **키 회전은 재기동이 필요하다** — 활성 키를 기동 시 확정해 보유한다. 무중단 회전은 JWKS에
   신·구 공개키를 함께 싣고 서명키만 교체하는 단계가 추가로 필요하다.
5. **auth-service는 Optional 프로파일이다** — 기본 `docker compose up`에는 포함되지 않으므로
   `--profile auth`(또는 `--profile full`)로 함께 띄워야 API 호출이 가능하다.
   외부 IdP를 쓰는 정수장은 `swtp.auth.jwks-uri`만 그쪽으로 돌리면 auth-service 없이 동작한다.
6. **`ws-ticket`은 게이트웨이까지만 구현됐다** — realtime-service가 아직 골격이라
   실제 SSE/WebSocket 핸드셰이크 연동은 Realtime 슬라이스의 몫이다. 규약(발급 API·검증 경로)은 확정됐다.
7. **서비스 간 직접 호출의 토큰 전파는 미결** — 아키텍처 3.1.3이 서비스 간 호출은 게이트웨이를 거치지
   않는다고 규정하므로, 그 경로의 인증 방식(서비스 계정 토큰 vs 사용자 토큰 릴레이)은
   실제 서비스 간 호출이 생기는 시점의 과제로 남긴다.

## 다음 단계

Phase 11(API 문서 통합) — 서비스별 `springdoc.api-docs.path`를 게이트웨이 라우트에 정렬하고,
게이트웨이에 `springdoc-openapi-starter-webflux-ui` + `swagger-ui.urls`로 8종을 집계한다.
JWT `SecurityScheme`을 추가해 swagger-ui의 Authorize로 인증 API를 호출할 수 있게 한다.
게이트웨이·서비스 양쪽 화이트리스트에 문서 경로를 이미 열어 두었으므로 배선만 남았다.
