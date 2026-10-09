# Step 18 — 게이트웨이 경로 규약 통일 · 프로파일 축 개방

- 일자: 2026-08-21
- 상태: ✅ 완료 (게이트웨이 테스트 25건 통과. 컨테이너 E2E는 미실행)
- 관련: [sync-01](../../sync/sync-01-20260821.md), 아키텍처 3.1·8.2·10장

## 발단

`docs/sync/sync-01-20260821.md`(CLAUDE.md 정합성 1회차 점검)에서 코드/문서 중 어느 쪽을 고칠지 미정인 항목 4건이 올라왔다. 그중 둘을 이 문서에서 결정한다.

- **D4** — `apps/gateway/CLAUDE.md`가 스스로 "아직 이전 방식(`/api/{prefix}/**`)으로 남은 라우트가 있다"고 적어 둔 상태였다. 실측하니 신방식 2개(master·auth) / 구방식 6개(telemetry·realtime·job·autonomous·ems·pms)로, **소수가 아니라 다수가 구방식**이었다.
- **D3** — `config-repo/CLAUDE.md`가 "프로파일 축은 환경 축(`docker`) 하나다"라고 규정하면서, 정작 `application-dev.yml`을 "규칙에서 벗어나 있다(정리 대상)"라고 자기 파일 목록에 적어 두고 있었다. 규칙과 현물이 어긋난 채 방치돼 있었다.

두 항목 모두 **문서가 낡은 게 아니라 문서가 옳고 현물이 어긋난** 경우여서, 문서를 현물에 맞추면 미완 상태가 완료로 세탁된다. 그래서 sync-01에서 손대지 않고 여기로 넘겼다.

## 결정 (2026-08-21)

### 1. 경로 규약은 `/{서비스명}/**` + `RewritePath` 하나로 통일한다

구방식 6개를 전부 옮긴다. **예외를 두지 않는다.**

규약이 둘이면 문서 URL·화이트리스트·티켓 경로가 각각 두 형태로 갈라지고, 어느 쪽인지는 라우트 정의를 봐야만 안다. 실제로 `ApiDocsRouteConsistencyTest`가 두 규약을 구분해 기대값을 계산하는 분기를 갖고 있었다 — 규약이 하나면 없어도 될 복잡도였다.

**기각한 대안 — 구방식으로 통일.** 라우트가 서비스의 경로 작명을 구속한다(아래 함정 #2). 또 `StripPrefix` 없이 접두사를 그대로 넘기면 게이트웨이가 서비스의 내부 경로 규칙을 알아야 하므로, 서비스가 경로를 바꿀 때마다 게이트웨이가 따라 움직인다.

### 2. 규약 이탈을 테스트로 잠근다

`ApiDocsRouteConsistencyTest.allRoutesFollowServiceNamePrefixConvention`을 추가한다. 모든 라우트에 대해 `Path` 접두사가 `/{라우트 id}`인지, `RewritePath` 필터가 있는지 검증한다.

문서에 "통일했다"라고 쓰는 것만으로는 규약이 유지되지 않는다 — sync-01이 찾아낸 D4 자체가 "문서에 적어 뒀는데 지켜지지 않은" 항목이었다. 같은 일이 반복되지 않도록 빌드가 실패하게 만든다.

### 3. 프로파일 축의 개수를 제한하지 않는다

"프로파일 축은 환경 축 하나다"를 철회한다. 환경 축(`docker`·`dev`)과 정수장(테넌트) 축을 필요한 만큼 만든다. 따라서 **`application-dev.yml`은 정리 대상이 아니다.**

축 개수 대신 **"한 키를 두 축이 함께 건드리지 않는다"** 를 남긴다. 이게 원래 규칙이 실제로 막으려던 것이다 — 같은 키를 두 축이 건드리면 누가 이기는지가 활성 프로파일 순서에 달리고, 어느 파일이 이겼는지는 로그에 남지 않는다.

> **이 결정은 [step-12](step-12-auth-mode-2values.md)의 후속 철회를 부분적으로 되돌린다.** step-12는 사이트 프로파일 축을 제거하고 `config-repo/application-<사이트>.yml` 두 개를 삭제하면서 "Spring 프로파일은 환경 축 하나"로 좁혔다. 여기서 되돌리는 것은 **축 개수 제한**뿐이고, `swtp.auth.mode`를 환경변수가 결정한다는 원칙은 그대로 둔다 — step-12가 실제로 막으려던 것은 "같은 키를 두 축이 건드려 활성 순서가 결과를 정하는" 상태였고, 그 금지는 새 규칙에 그대로 남겼다. 정수장별 프로파일을 다시 만들 때 `swtp.auth.mode`를 거기 넣으면 step-12의 문제가 그대로 재발한다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `apps/gateway/src/main/resources/application.yml` | 라우트 6종을 `Path=/{서비스명}/**` + `RewritePath`로 교체. 문서 URL 6종에 서비스명 접두사 추가. `ticket-auth-paths`를 `/realtime-service/**`로. 구방식 전용 화이트리스트 2행(`/api/*/v3/api-docs/**`, `/api/*/swagger-ui/**`) 제거 |
| `apps/gateway/src/test/.../ApiDocsRouteConsistencyTest.java` | 규약 잠금 검증 추가. 화이트리스트 기대값에서 구방식 항목 제거 |
| `apps/gateway/src/test/.../JwtAuthGlobalFilterTest.java` | 티켓 경로 픽스처를 새 규약으로 (운영 설정과 형태 일치) |
| `apps/realtime-service/.../TelemetryStreamController.java` | 게이트웨이 라우트 표기 주석 정정. **엔드포인트 경로는 변경 없음** |
| `apps/job-service/.../sample/SampleJobController.java` | 같음 |
| `config-repo/CLAUDE.md` | 프로파일 축 규칙 교체. `application-dev.yml`의 "정리 대상" 표기 삭제 |
| `apps/gateway/CLAUDE.md` | 라우팅 절 재작성 — 자체 선언한 부채 문구 삭제, 화이트리스트·티켓 경로의 판정 시점 명시 |
| `docs/architecture/…아키텍처.md` | 문서 집계 다이어그램·경로 규약 문단·WS 티켓 흐름(3장·10장) 정정 |
| `config-repo/application-dev.yml` | **변경 없음** (유지 결정) |
| 서비스들의 `@RequestMapping` 경로 | **변경 없음** — 바뀐 것은 게이트웨이 외부 접두사뿐이다 |
| `docs/scaffold/steps/step-07·08·10` | **변경 없음** — 그 시점의 결정 기록이라 소급 수정하지 않는다 |

---

## 구현 상세

### 경로가 갈리는 지점

```text
브라우저            /telemetry-service/api/telemetry/sample-measurements
                          │
                    [게이트웨이]
                    RequestId(-2147483648) → AccessLog(+10) → JwtAuth(+20)
                          │                                      ↑
                          │              화이트리스트·티켓 경로는 여기서 판정된다
                          │              = RewritePath 이전의 외부 경로
                    RewritePath (라우트 필터, order 1)
                          │  /telemetry-service 를 벗긴다
                          ▼
telemetry-service   /api/telemetry/sample-measurements
```

`JwtAuthGlobalFilter`는 `GlobalFilter`이고 order가 `HIGHEST_PRECEDENCE + 20`이다. 라우트 필터(`RewritePath`)는 라우트 안 선언 순서대로 1부터 매겨지므로 **전역 필터가 항상 먼저 돈다.** 그래서 `permit-all-paths`와 `ticket-auth-paths`는 접두사가 붙은 외부 경로로 써야 한다.

### 문서 URL 유도

서비스가 스펙을 노출하는 경로는 그대로 `/api/{prefix}/v3/api-docs`다(`swtp-web-starter`가 `spring.application.name`에서 유도). 게이트웨이에서 본 주소만 접두사를 한 겹 더 갖는다.

| 서비스 | 서비스 내부 | 게이트웨이 경유 |
|---|---|---|
| master-service | `/api/master/v3/api-docs` | `/master-service/api/master/v3/api-docs` |
| telemetry-service | `/api/telemetry/v3/api-docs` | `/telemetry-service/api/telemetry/v3/api-docs` |
| (8종 전부 동일 형태) | | |

화이트리스트는 `/*-service/api/*/v3/api-docs/**` 한 줄이 8종을 모두 덮는다. 구방식 전용이던 `/api/*/v3/api-docs/**`·`/api/*/swagger-ui/**`는 매칭될 라우트가 사라져 제거했다.

---

## 함정 기록

1. **화이트리스트에서 죽은 항목을 지우면 테스트가 깨진다.** `apiDocsPathsAreWhitelisted`가 `/api/*/v3/api-docs/**`가 목록에 있음을 단언하고 있었다. 설정만 지우고 테스트를 두면 빌드가 실패하고, 반대로 테스트만 느슨하게 만들면 화이트리스트 누락을 잡던 그물이 함께 사라진다. → 두 곳을 같은 커밋에서 옮기고, 단언 대상을 통일 후에도 유효한 `/*-service/api/*/v3/api-docs/**`로 바꿨다.

2. **구방식 라우트는 서비스의 경로 작명을 구속하고 있었다.** `Path=/api/job/**`은 세그먼트 단위로 매칭하므로 `/api/jobs`(복수형)에 도달하지 못한다 — `job` ≠ `jobs`다. 아키텍처 8.2가 설계해 둔 Job REST API(`GET /api/jobs`, `GET /api/job-executions/{id}`)는 구방식 라우트 아래에서는 게이트웨이 경유로 **전부 404**였다. 접두사를 벗기는 방식은 나머지 경로를 그대로 넘기므로 이 제약이 없다. 통일은 규약 정리이기도 하지만 **잠재 결함 제거이기도 했다.**

3. **`ticket-auth-paths`를 놓치기 쉽다.** 라우트·문서 URL·화이트리스트는 `ApiDocsRouteConsistencyTest`가 잡지만 티켓 경로는 그 테스트의 대상이 아니다. 이 값이 낡으면 **SSE/WebSocket 핸드셰이크만 401**이 되고, 일반 REST는 멀쩡해서 라우팅 문제로 보이지 않는다. → `apps/gateway/CLAUDE.md`의 "함께 갱신할 곳" 목록에 추가했다.

4. **`sync-01`이 D4를 "문서가 낡았다"로 처리했다면 부채가 사라졌을 것이다.** 문서는 옳았고 코드가 안 따라온 상태였다. 정합성 점검이 불일치를 무조건 문서 수정으로 해소하면, 미완 작업이 기록상 완료로 바뀐다.

## 알려진 한계

1. **외부 클라이언트의 호출 주소가 바뀐다.** 6개 서비스의 게이트웨이 경유 주소가 `/api/{prefix}/...` → `/{서비스명}/api/{prefix}/...`로 이동했다. 이 리포에 프론트엔드가 없어 영향 범위를 검증할 수 없다 — 프론트 배포와 동기화가 필요하다. 특히 실시간 SSE는 `/api/realtime/telemetry/stream` → `/realtime-service/api/realtime/telemetry/stream`이다.

2. **컨테이너 E2E는 돌리지 않았다.** 게이트웨이의 설정 정합성 테스트와 필터 단위 테스트까지만 통과했다. 실제 라우팅·SSE 핸드셰이크는 compose 기동 후 확인해야 한다.

3. **`application-dev.yml`이 `swtp.auth.mode: none`을 yml에 직접 둔다.** `config-repo/CLAUDE.md`의 "`swtp.auth.mode`는 환경변수가 결정한다 — yml 파일로 되돌려 놓지 않는다"와 충돌한다. 결정 3으로 dev 프로파일 자체는 유지하기로 했으므로 이 충돌은 남았다. `${SWTP_AUTH_MODE:none}` 형태로 바꿔 환경변수 우선을 지키는 안이 있으나, 별건이라 이번에 손대지 않았다.

## 검증

```bash
./gradlew :apps:gateway:test --console=plain
./gradlew :apps:realtime-service:compileJava :apps:job-service:compileJava
grep -c 'RewritePath=' apps/gateway/src/main/resources/application.yml   # 라우트 수와 같아야 한다
grep -c 'Path=/api/'   apps/gateway/src/main/resources/application.yml   # 구방식 술어 — 0이어야 한다
```

> 잔존 확인은 **`Path=` 술어만** 본다. `/api/telemetry` 같은 문자열로 훑으면 문서 URL
> (`/telemetry-service/api/telemetry/v3/api-docs`)이 6건 잡히는데, 그 `/api/{prefix}` 구간은
> 구방식 잔존이 아니라 **서비스가 실제로 노출하는 경로**라 남아 있는 것이 정상이다.

### 실측 결과 (2026-08-21)

```text
── 테스트 ──────────────────────────────────────────
> :apps:gateway:test                           BUILD SUCCESSFUL (35s)
  ApiDocsRouteConsistencyTest      tests=4  failures=0  errors=0
    · swagger-ui.urls는 라우트 접두사와 1:1로 대응한다
    · 모든 라우트가 lb://{라우트 id}를 가리킨다
    · 문서 경로가 인증 화이트리스트로 열려 있다
    · 모든 라우트가 /{서비스명}/** + RewritePath 규약을 따른다   ← 신규
  JwtAuthGlobalFilterTest          tests=14 failures=0  errors=0
  RequestIdGlobalFilterTest        tests=4  failures=0  errors=0
  AccessLogGlobalFilterTest        tests=2  failures=0  errors=0
  GatewayApplicationTest           tests=1  failures=0  errors=0
                                   ─────────────────
                                   합계 25건 전부 통과

> :apps:realtime-service:compileJava           BUILD SUCCESSFUL
> :apps:job-service:compileJava                BUILD SUCCESSFUL

── 규약 ────────────────────────────────────────────
> 라우트 수                                    8
> RewritePath= 개수                            8   → 전 라우트가 접두사를 벗긴다
> Path=/api/ 술어                              0   → 구방식 라우트 없음
                                               → 통일 완료
```

### 운영 확인 지점

compose 기동 후 아래를 확인한다. 통일이 깨지면 증상이 서로 다르게 나타난다.

- `http://<gateway>:8080/swagger-ui.html` — 드롭다운 8종이 **모두** 스펙을 그린다. 빈 항목이 있으면 그 서비스의 화이트리스트 또는 문서 URL이 어긋난 것이다.
- `GET /telemetry-service/api/telemetry/sample-measurements` — 200. 404면 `RewritePath` 정규식이 어긋난 것이다.
- SSE 핸드셰이크 `GET /realtime-service/api/realtime/telemetry/stream?ticket=<티켓>` — 401이면 `ticket-auth-paths`가 낡은 것이다(함정 #3).

## 다음 단계

**프론트엔드 주소 동기화.** 한계 #1이 유일한 외부 영향이다. 프론트 배포 전에 게이트웨이를 올리면 기존 클라이언트가 전부 404를 본다.

**sync-01의 나머지 2건(D5·D6) 결정.** master-service 모듈 문서 부재와 `AbstractIntegrationTest` 5/11이 남았다. D5는 "단위 서비스별 CLAUDE.md로 관리할 것인가"라는 더 큰 질문과 묶여 있다.

**한계 #3(dev 프로파일의 `auth.mode`) 처리.** 규칙과 현물이 어긋난 상태가 또 하나 남았다 — 이번에 D3를 남긴 것과 같은 종류의 부채다.
