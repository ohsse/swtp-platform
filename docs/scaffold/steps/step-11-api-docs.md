# Step 11 — API 문서 통합 (게이트웨이 집계 swagger-ui)

- 일자: 2026-08-14
- 상태: ✅ 완료 (전체 스택 기동 + 게이트웨이 경유 집계 UI·Authorize 호출 검증까지 완료)
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 11

## 목표

"게이트웨이를 통해 서비스별 스웨거에 접근한다"는 요구를 구현한다.
단일 진입점(`http://localhost:8080/swagger-ui.html`) 한 곳에서 드롭다운으로 서비스를 전환하고,
Authorize에 JWT를 넣어 인증이 필요한 API까지 실제로 호출할 수 있는 상태를 만든다.

> 결정 (2026-08-14):
> - **집계 UI는 게이트웨이 단독 호스팅, 서비스는 스펙(JSON)만 노출한다** — 서비스마다 UI를 게이트웨이로
>   열어주는 대안 기각. 그러려면 `springdoc.webjars.prefix`까지 서비스별로 접두사를 붙여야 하고
>   (정적 자원이 `/swagger-ui/**`로 나가는데 게이트웨이 라우트는 `/api/*/**`뿐이라 404), 결과적으로
>   같은 화면이 9개가 된다. 진입점을 하나로 두는 것이 애초의 요구이기도 하다.
> - **문서 경로는 `spring.application.name`에서 유도한다** — 8개 서비스 × 2개 설정 파일에 손으로 적는 대안 기각.
>   라우트와 어긋나는 순간 조용히 404가 되는데, 기동은 정상이라 발견이 늦다.
> - **`servers`는 상대 URL(`/`)로 고정한다** — springdoc 기본 동작(요청 Host로 계산) 기각.
>   게이트웨이가 프록시하면 서비스가 보는 Host는 `master-service:8081`이라 브라우저가 도달할 수 없는
>   주소가 스펙에 박힌다. `X-Forwarded-*` + `forward-headers-strategy` 대안은 게이트웨이 경유에서만
>   맞고 서비스 직접 접근에서 어긋난다. 상대 URL은 양쪽 모두에서 옳다.
> - **Bearer 스킴은 web-starter가 전역으로 선언한다** — 집계 UI의 Authorize 버튼은 *선택된 스펙*이
>   선언한 securityScheme만 보여준다. 서비스가 각자 선언하는 대안은 한 곳만 빠뜨려도
>   그 서비스만 토큰 입력이 불가능해진다.

## 실행 내용

### 11-1. 문서 경로를 게이트웨이 라우트에 정렬

게이트웨이 라우트는 `Path=/api/{prefix}/**`이고 **StripPrefix가 없다**. 경로가 그대로 전달되므로,
서비스가 springdoc 기본값 `/v3/api-docs`에 문서를 두면 게이트웨이를 통해 도달할 라우트가 존재하지 않는다.

`starters/swtp-web-starter/.../SwtpOpenApiEnvironmentPostProcessor` 신설:

```java
// master-service → master → /api/master/v3/api-docs
String name = environment.getProperty("spring.application.name");
String routePrefix = name.endsWith("-service")
        ? name.substring(0, name.length() - "-service".length())
        : name;
defaults.put("springdoc.api-docs.path", "/api/" + routePrefix + "/v3/api-docs");
environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
```

- `addLast()`(최저 우선순위) — 앱이나 config-server가 명시한 값이 항상 이긴다.
  springdoc 자체 기본값은 프로퍼티 소스가 아니라 코드 상수라 이 주입이 우선한다
- 규칙에서 벗어난 서비스는 `swtp.openapi.route-prefix`로 직접 지정한다 (탈출구)
- `META-INF/spring.factories`에 `org.springframework.boot.EnvironmentPostProcessor`로 등록
  (기존 4개 스타터와 동일한 Boot 4 신 인터페이스 키)

`Ordered`를 구현하지 않아 최저 우선순위로 실행된다 — ConfigData 단계(application.yml, config-server)
이후이므로 `spring.application.name`을 읽을 수 있다.

### 11-2. 공통 명세에 Bearer 스킴 + 상대 서버 URL

`SwtpOpenApiAutoConfiguration`을 확장했다.

```java
.servers(List.of(new Server().url("/").description("현재 오리진")))
.components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
```

전역 `security` 요구사항을 거는 이유: 플랫폼 기본 정책이 "화이트리스트 외 전부 인증"이라
대다수 엔드포인트가 토큰을 요구한다. 공개 엔드포인트는 컨트롤러에서 `@SecurityRequirements`(빈 값)로 해제한다.

### 11-3. 게이트웨이 집계 UI

`apps/gateway/build.gradle`에 `springdoc-openapi-starter-webflux-ui` 추가
(게이트웨이는 WebFlux — web-starter가 쓰는 webmvc-ui가 아니다).

`application.yml`에 8종을 등록했다.

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    urls:
      - { name: master-service,   url: /api/master/v3/api-docs }
      # ... 8종
    urls-primary-name: master-service
```

**`config-repo/gateway-docker.yml`에는 복제하지 않았다.** url이 전부 상대 경로라 로컬/컨테이너에서
동일하게 동작하기 때문이다. 계획서가 경고한 "라우트 리스트가 인덱스 단위로 치환된다"는 함정을
설정을 한 곳에만 두는 것으로 회피했다 — 양쪽에 두면 정합성 유지 대상이 하나 더 늘어난다.

### 11-4. 정합성 테스트

설정 파일 간의 약속이라 컴파일러가 잡아주지 못한다. `ApiDocsRouteConsistencyTest`로 못박았다.

| 테스트 | 잡는 사고 |
|---|---|
| `swaggerUrlsMatchRoutes` | 라우트 없는 서비스를 드롭다운에 넣거나, 라우트만 추가하고 문서 등록을 빠뜨림 |
| ~~`dockerRoutesMatchLocalRoutes`~~ | `gateway-docker.yml`이 라우트를 인덱스 단위로 치환 → 컨테이너에서만 라우팅 소실 |
| `apiDocsPathsAreWhitelisted` | 문서 경로가 인증 화이트리스트에서 빠져 UI가 401만 받음 |

> **이후 변경**: Eureka 도입으로 라우트 uri가 `lb://{서비스명}`이 되면서 `gateway-docker.yml` 자체가
> 사라졌다. `dockerRoutesMatchLocalRoutes`는 같은 사고를 더 앞에서 막는 `routesResolveThroughServiceRegistry`
> (모든 라우트가 호스트·포트 없이 `lb://`를 쓰는지 검사)로 대체되었다. 아키텍처 3.1.2 참조.

`YamlPropertySourceLoader`로 두 yml을 직접 읽어 비교한다 — 앱 컨텍스트를 띄우지 않으므로
config-server 가동 여부에 결과가 좌우되지 않는다.

## 문제와 해결

| # | 문제 | 원인 | 해결 |
|---|---|---|---|
| 1 | `/v3/api-docs`가 **500 + ERROR 로그** (Phase 11이 드러낸 잠복 결함) | 문서를 `/api/master/v3/api-docs`로 옮기자 구 경로가 "인증은 통과하지만 매핑은 없는" 상태가 됐다. `GlobalExceptionHandler`의 catch-all `@ExceptionHandler(Exception.class)`가 `NoResourceFoundException`(404를 들고 있는 예외)을 삼켜 500으로 변환 | `NoResourceFoundException`/`NoHandlerFoundException` 전용 핸들러를 추가해 COMMON-404 + DEBUG 로그로 격하. `GlobalExceptionHandlerTest` 신설 |
| 2 | Testcontainers 통합 테스트 2회 실패 (`Could not find a valid Docker environment`, Kafka 컨테이너 기동 실패) | 전체 스택이 뜬 상태에서 여러 테스트 JVM이 동시에 컨테이너를 요청해 Docker 데몬이 일시적으로 응답하지 못했다. 코드 무관 | 재실행으로 통과. 스택 가동 중 전체 빌드는 자원 경합이 있음을 인지 |
| 3 | `springdoc-openapi-starter-webflux-ui` 3.1.0 미캐시 | 카탈로그에는 좌표만 있고 실제로 쓰인 적이 없었다 | 최초 빌드에서 정상 다운로드 확인 |

### 1번이 왜 중요한가

클라이언트 오타 URL 하나가 **서버 장애(500)로 보고**된다. 500 기준 알림이 오탐으로 울리고
진짜 장애가 소음에 묻힌다. 게다가 인증 화이트리스트가 넓어질수록 잘 발생한다 —
차단되는 경로는 401에서 끝나지만, 열린 경로는 디스패처까지 도달하기 때문이다.

## 검증 결과

`gradlew build` 그린(전 모듈), 전체 스택 기동 후 컨테이너 네트워크 내부에서 검증했다.
(호스트 포트 예약 문제로 `compose.no-publish.yaml` 오버라이드 + `curlimages/curl` 사용 — step-10과 동일)

```text
✅ gradlew build                                          BUILD SUCCESSFUL (신규 테스트 9건 포함)
✅ GET  /swagger-ui.html                                  302 -> /swagger-ui/index.html
✅ GET  /swagger-ui/index.html | swagger-ui.css | swagger-initializer.js   200
✅ GET  /v3/api-docs/swagger-config
        urls 8종 등록 확인, "urls.primaryName":"master-service"
✅ GET  /api/{master,telemetry,realtime,job,auth}/v3/api-docs   전부 200
✅ 스펙 내용 (master-service)
        "servers":[{"url":"/","description":"현재 오리진"}]        ← 상대 URL
        "security":[{"bearerAuth":[]}]                            ← 전역 요구사항
        "securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}}
        "paths":{"/api/master/sample-items/{id}": ...}            ← 게이트웨이 라우트와 정렬
✅ Try it out 재현 (스펙의 server "/" + path 조합 호출)
        토큰 없이 GET /api/master/sample-items        401
        Authorize 후 동일 호출                        200
✅ 구 기본 경로 GET :8081/v3/api-docs                 {"code":"COMMON-404"} [404]   (이전 500)
✅ 위 404 요청 이후 master-service ERROR 로그 건수     0
✅ 서비스 직접 접근 :8081/swagger-ui.html             302 -> /swagger-ui/index.html (여전히 동작)
```

## 결정 사항 및 알려진 한계

1. **미기동 Optional 서비스는 드롭다운에서 500을 받는다** — `autonomous`/`ems`/`pms`는 Optional
   프로파일이라 기본 스택에 없다. 드롭다운에는 8종이 모두 뜨지만 그 셋을 고르면 스펙 로드에 실패한다.
   `--profile full`로 띄우면 해결된다. 목록을 동적으로 만들려면 discovery-server 기반 집계가 필요한데,
   현재 라우팅이 정적 URI 방식(Phase 3 결정)이라 문서만 동적으로 만드는 것은 일관성을 해친다.
2. **`GlobalExceptionHandler`는 아직 404만 되살렸다** — 405(Method Not Allowed),
   415(Unsupported Media Type), 잘못된 JSON 본문(`HttpMessageNotReadableException`) 등은 여전히
   catch-all에 걸려 500이 된다. Spring의 `ErrorResponse` 인터페이스를 활용해 상태코드를 보존하는
   일괄 처리가 다음 후보다. 이번에는 Phase 11이 유발한 회귀(404)만 범위에 넣었다.
3. **집계 목록은 손으로 관리한다** — 게이트웨이 `application.yml`의 `swagger-ui.urls` 8종은 수동이다.
   라우트에서 자동 유도하지 않은 이유는 표시 이름과 순서를 통제하고 싶어서다.
   대신 `ApiDocsRouteConsistencyTest`가 라우트와의 어긋남을 빌드 시점에 잡는다.
4. **게이트웨이 자신도 `/v3/api-docs`에 빈 스펙을 노출한다** — 컨트롤러가 없어 `paths`가 비어 있다.
   `springdoc.api-docs.enabled=false`로 끄는 것을 검토했으나, 드롭다운 목록의 출처인
   `/v3/api-docs/swagger-config`가 같은 조건에 걸릴 위험이 있어 켜 둔 채로 두었다.
5. **문서 경로는 인증 없이 공개다** — UI가 뜨기 전에는 토큰을 얻을 화면 자체가 없으므로 불가피하다.
   운영에서 API 스펙 노출을 막으려면 `springdoc.api-docs.enabled=false`를 운영 프로파일에 두거나,
   게이트웨이 화이트리스트에서 문서 경로를 빼고 내부망에서만 접근하게 해야 한다.
   **운영 반입 전 결정이 필요한 항목**이다.

## 다음 단계

Phase 0~11의 골격·수직 슬라이스·기반 정비가 모두 끝났다. 남은 것은 실도메인 구현이며,
그 전에 처리해야 할 **운영 반입 전 필수 조치**가 누적되어 있다.

- 보안: 서명 개인키 평문 보관(step-10), `config-repo` 평문 DB 비밀번호, Grafana `admin/admin`,
  기본 계정 `admin/admin123!`, 운영에서의 API 스펙 노출 여부(위 5번)
- 관측: 서비스 간 직접 호출의 `X-Request-Id` 릴레이(step-09), 토큰 전파 방식(step-10)
- 기능: realtime-service의 `ws-ticket` 핸드셰이크 연동(step-10)
