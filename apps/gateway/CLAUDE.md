# gateway — 외부 단일 진입점

**WebFlux 앱이다.** 서블릿 스택이 클래스패스에 들어오면 Boot가 웹 타입을 SERVLET으로 판정해 게이트웨이 자동구성이 통째로 꺼진다(에러 없이). 의존을 추가할 때 `spring-boot-starter-web`/`-webmvc` 전이를 exclude한다. 같은 이유로 서블릿 전용인 `swtp-web-starter`/`swtp-security-starter`를 쓰지 않고, springdoc도 `-webflux-ui`다.

## 인증

**Spring Security를 얹지 않는다.** `spring-security-oauth2-jose`의 `ReactiveJwtDecoder`만 빌려 `JwtAuthGlobalFilter`가 직접 검증한다 — 시큐리티 체인을 얹으면 인증 판단 지점과 경로 화이트리스트가 두 곳으로 갈린다.

여기서 통과시켜도 각 서비스가 다시 검증한다(다중 방어). 그래서 `swtp.auth.mode` 스위치는 게이트웨이가 아니라 `config-repo`에 있다 — 게이트웨이만 꺼도 서비스가 401을 낸다.

모드는 `none`(**기본**, 검증 안 함) / `internal`(검증함) 2값이고, 결정은 `SWTP_AUTH_MODE` 환경변수가 내린다. 기본이 `none`인 것은 배포 현실을 따른 것이다 — 다수 정수장이 인증·인가를 앞단 포털/SSO에서 끝내고 넘긴다. **`none`에서도 클라이언트가 보낸 `X-User-*` 위조 헤더는 그대로 지운다** — "인증을 안 한다"와 "클라이언트가 자기를 관리자라고 주장할 수 있다"는 다른 문제라서, 앞단이 사용자 정보를 헤더로 실어도 다운스트림에는 오지 않는다. **외부 IdP 값은 두지 않는다** — 검증 경로가 `typ` 커스텀 클레임을 요구해 표준 IdP 토큰으로는 통하지 않으므로, 값만 늘리면 "설정은 있는데 전부 401"이 된다.

토큰 검증 실패는 원인에 따라 응답이 갈린다 — 서명 불일치·만료(`BadJwtException`)는 401, JWKS를 조회하지 못한 경우는 **503**이다. 인증 인프라 장애가 토큰 오류로 위장되면 클라이언트가 재로그인만 반복한다.

## GlobalFilter 순서

| order | 필터 | 역할 |
|---|---|---|
| `HIGHEST_PRECEDENCE` | `RequestIdGlobalFilter` | `X-Request-Id` 발급·전파 |
| `+10` | `AccessLogGlobalFilter` | 접근 로그 |
| `+20` | `JwtAuthGlobalFilter` | 토큰 검증, `X-User-*` 주입, 클라이언트 위조 헤더 제거 |

필터를 추가하면 이 표를 갱신한다.

## 라우팅

`uri: lb://<서비스명>`만 쓴다 — 호스트·포트를 적지 않으므로 로컬·컨테이너·다중 노드에서 같은 목록이 그대로 동작한다. **이것이 `config-repo/gateway-docker.yml`이 없는 이유다**: 정적 URI로 되돌리면 라우트 목록을 환경별로 복제해 인덱스 단위로 덮어쓰는 구조가 부활한다.

**경로 규약은 `/{서비스명}/**` + `RewritePath`로 접두사를 벗겨 넘기는 방식 하나다.** `lb://` 레지스트리 라우팅으로 옮기면서 정했고 step-18에서 전 라우트를 통일했다 — **예외를 만들지 않는다.** 라우트 접두사가 서비스명과 같아 어느 서비스로 가는지가 URL에 그대로 드러나고, `ApiDocsRouteConsistencyTest`가 규약 이탈을 빌드 시점에 실패시킨다.

접두사를 벗기면 **게이트웨이에서 본 주소와 서비스가 노출하는 경로가 갈라진다.** 서비스는 `/api/{prefix}/...`에 노출하므로 게이트웨이 주소는 `/{서비스명}/api/{prefix}/...`가 된다. 문서 URL·화이트리스트가 이 접두사를 한 겹 더 갖는 이유다.

접두사를 벗기는 방식이라 **게이트웨이는 서비스가 어떤 경로를 노출하는지 알 필요가 없다.** 구방식(`Path=/api/{prefix}/**`을 그대로 넘김)은 세그먼트 단위로 매칭해서, 예컨대 `/api/job/**` 라우트로는 `/api/jobs`(아키텍처 8.2의 Job API)에 도달할 수 없었다 — 라우트가 서비스의 경로 작명까지 구속했다.

라우트를 추가하거나 **접두사를 바꾸면** 함께 갱신할 곳 — `springdoc.swagger-ui.urls`, `swtp.gateway.security.permit-all-paths`, `swtp.gateway.security.ticket-auth-paths`(스트리밍 경로), compose 프로파일, **`SWTP_AUTH_JWKS_URI`**(compose 2종 + `.env.example`). 마지막 것은 JWKS를 **게이트웨이 경유로** 조회하기 때문에 이 접두사에 묶인다 — 놓치면 `mode=internal`에서 전 요청이 503이다. 앞의 둘만 `ApiDocsRouteConsistencyTest`가 빌드 시점에 검증하고 **나머지 셋은 검증 장치가 없다.** **화이트리스트·티켓 경로는 `RewritePath` 이전의 외부 경로로 쓴다** — `JwtAuthGlobalFilter`가 `pathWithinApplication()`으로 판정하고, 그 필터가 라우트 필터보다 먼저 돈다.

`Path` 술어에 `/**`를 빠뜨리지 않는다 — `Path=/master-service/`는 정확히 그 한 경로만 매칭해 하위 경로가 전부 404가 된다. 라우트는 살아 있고 매칭만 안 되는 상태라 로그에도 단서가 없다.

CORS는 여기 한 곳에만 둔다. 서비스에서 또 설정하면 헤더가 중복돼 브라우저가 거부한다.
