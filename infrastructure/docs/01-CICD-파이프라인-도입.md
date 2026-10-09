# 01 — 개발서버 CI/CD 파이프라인 도입 (Jenkins)

- 일자: 2026-08-24
- 상태: 🔍 논의 → 구현 완료, **개발서버 실측 미실행** (아래 「검증」 절차를 개발서버에서 밟아야 ✅로 바뀐다)
- 검토 관점: 배포·운영 · 빌드 규약 · 단일 서버 인프라
- 리뷰: 미실시
- 관련: `infrastructure/CLAUDE.md`「배포 구분」, `docs/scaffold/steps/step-05-containerization.md`(공통 Dockerfile·healthcheck 체인의 출처), `docs/scaffold/steps/step-18-gateway-route-convention.md`(스모크 테스트가 쓰는 경로 규약), `docs/scaffold/steps/step-20-integration-test-baseline-scope.md`(CI가 반드시 Docker를 필요로 하는 이유), `apps/CLAUDE.md`「포트 · 소유 스키마 · 배포 구분」, 절차서 `infrastructure/docs/02-Jenkins-개발서버-구축절차.md`

## 발단

개발서버에 Jenkins가 이미 **Windows 서비스**로 돌고 있는데, 이 리포지토리에는 CI/CD 흔적이 하나도 없다.
전수 조사 결과 `Jenkinsfile`·`.github/`·`.gitlab-ci.yml`·배포 스크립트가 없고,
`docs/미착수.md`·`docs/scaffold/plan.md`·아키텍처 21개 장 어디에도 빌드/배포 자동화 서술이 없다.
CI/CD는 **기각된 주제가 아니라 한 번도 올라온 적 없는 주제**다.

현재 배포 경로는 `infrastructure/CLAUDE.md`가 고정한 수동 2단계뿐이다.

```bash
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml up -d --build
```

이 작업은 **새 배포 방식을 만드는 일이 아니다.** 위 두 줄을 Jenkins가 대신 치게 만들고,
그 앞뒤에 검증과 되돌리기를 붙이는 일이다. 그래서 설계의 대부분은 "무엇을 새로 만들까"가 아니라
"기존 규약 중 무엇을 그대로 재사용할 수 있는가"를 확인하는 데 쓰였다.

개발서버는 앱을 **아직 한 번도 돌린 적이 없다.** 즉 이 파이프라인의 첫 실행이 곧 개발서버 최초 기동이다.
실패 확률이 가장 높은 국면이므로 롤백을 1차 범위에 포함했다.

### 재사용할 수 있었던 것 (조사 결과)

| 이미 있는 것 | 파이프라인이 얻은 것 |
|---|---|
| `infrastructure/docker/app.Dockerfile` — 전 Java 앱 공통 템플릿, `APP` 빌드 인자 | 앱별 Dockerfile을 만들 필요가 없다 |
| `compose.yaml`의 `x-app-build` 앵커 (`context: ../..`) | 이미지 빌드 명령이 서비스마다 갈리지 않는다 |
| 전 앱 actuator healthcheck (`retries: 24`, `start_period: 20s`) | `compose up --wait`가 그대로 배포 완료 판정이 된다 |
| `compose.yaml`의 `profiles` (Optional 4종에만 부여) | `up -d`에 서비스를 나열하지 않아도 정확히 Core만 뜬다 |
| gateway `permit-all-paths`의 `/*-service/actuator/**` | 스모크 테스트가 토큰 없이 게이트웨이를 관통할 수 있다 |
| step-18의 경로 규약 `/{서비스명}/**` + `RewritePath` | 관통 확인 URL이 한 형태로 통일돼 있다 |

**새로 만든 것은 Jenkinsfile 1개와 PowerShell 스크립트 2개가 전부다.** `compose.yaml`은 손대지 않았다.

## 결정 (2026-08-24)

### 1. 레지스트리를 두지 않는다

Jenkins와 앱이 **같은 서버의 같은 Docker 데몬**을 쓴다. `docker compose build`가 끝난 순간
이미지는 이미 그 데몬 안에 있다. push/pull 왕복은 순수 낭비다.

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| Harbor / `registry:2` 사내 구축 | 지금 얻는 것이 0이다. 레지스트리가 값을 갖는 시점은 "이미지를 만든 기계와 실행할 기계가 다를 때"이고, 그건 정수장 현장 서버로 나갈 때다. 그때 필요한 것도 컨테이너 하나가 아니라 태그 규약이다 |
| Docker Hub 등 외부 레지스트리 | 온프레미스 폐쇄망 정수장이 최종 배포 대상이다. 외부 의존을 배포 경로에 심는 것은 방향이 반대다 |

### 2. `Jenkinsfile`을 리포지토리 루트에 커밋한다 (Declarative)

Jenkins UI에 스크립트를 붙여넣지 않는다. Jenkins job 설정에는 "이 리포지토리의 Jenkinsfile을 읽어라"만 남긴다.

파이프라인 정의가 코드와 같은 커밋에 들어가므로, 빌드 방식이 바뀐 이유가 git log에 남는다.
Jenkins를 재설치해도 파이프라인이 사라지지 않는다.

### 3. Jenkins workspace를 고정하고 지우지 않는다 ★

일반적인 CI 원칙(clean checkout)을 **의도적으로 어긴다.** 근거는 `compose.yaml`의 bind mount다.

```yaml
volumes:
  - ../../config-repo:/config-repo:ro    # config-server가 런타임 내내 읽는다
  - ../../logs:/logs                     # 전 앱이 런타임 내내 쓴다
```

경로가 상대경로이므로 **컨테이너가 checkout 디렉토리를 계속 참조한다.** workspace를 청소하면
살아 있는 스택의 설정 소스가 증발하고 로그 목적지가 끊긴다.

→ `customWorkspace 'C:\swtp\ws'`로 짧고 고정된 경로를 쓰고 `deleteDir()`을 하지 않는다.
빌드 산출물 정리는 `gradlew clean`이 담당한다.

부수 효과가 오히려 이득이다 — checkout이 `config-repo/`를 갱신하므로 **설정 변경도 배포에 함께 실린다.**

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| 기본 workspace (`workspace/<job명>`) | 경로가 길어 Windows MAX_PATH와 Gradle 캐시 문제를 부른다. 더 나쁜 것은 job 이름을 바꾸면 workspace 경로가 바뀌고, 그 순간 떠 있는 컨테이너의 마운트가 끊긴다는 점이다 |
| 배포 전용 디렉토리로 robocopy 복사 | 빌드와 런타임을 갈라 더 안전하지만, "빌드한 것과 배포한 것이 같은지"를 보장할 장치를 따로 만들어야 한다. 단일 서버·1인 개발에 과하다 |

### 4. 이미지 백업 태그(`:prev`)로 롤백한다 — `compose.yaml`은 손대지 않는다

`docker compose build`는 이미지를 `swtp-platform-<서비스>:latest`로 **덮어쓴다.** 그대로 두면
직전 버전이 사라져 되돌릴 수단이 없다.

빌드 **직전**에 현재 `:latest`에 `:prev` 태그를 붙인다. 배포가 실패하면 `:prev`를 `:latest`로
되돌리고 Core 8종만 재생성한다.

**기각한 대안**

| 대안 | 기각 사유 |
|---|---|
| 각 서비스에 `image: swtp/<name>:${SWTP_IMAGE_TAG}` 추가 | 정공법이고 N단계 롤백까지 열리지만, `compose.yaml` 8곳 수정 + `.env` 축 추가 + 태그 채번 규약이 한꺼번에 따라온다. 최초 기동을 앞둔 지금은 **compose 무수정**이 롤백 자체의 리스크를 낮춘다 → 「다음 단계」 2로 이월 |
| 롤백 없음 | 개발서버 최초 기동이라 실패 확률이 가장 높은 국면이다. 되돌릴 수 없는 배포를 이 시점에 두지 않는다 |

### 5. 배포 단위는 Core 8종 통째다

`gateway`를 단독으로 띄울 수 없다. `compose.yaml`의 `depends_on`이 master·telemetry·realtime·job·ems의
`service_healthy`를 요구한다. 이것은 결함이 아니라 step-05가 세운 의도된 성질이고, 주석이 근거를 남겨 뒀다 —
*"게이트웨이가 떴다 = 라우팅이 실제로 된다"는 성질이 스모크 테스트에서 유용하다*.

그래서 `docker compose up -d`를 **서비스 인자 없이** 부른다. `profiles`가 붙지 않은 서비스,
즉 Core 8종 + 미들웨어가 정확히 대상이 되고, Optional 4종은 자동으로 빠진다.
**배포 명령에 서비스 목록을 나열할 필요가 없다** — 목록이 두 곳에 생기면 반드시 어긋난다.

### 6. 스모크 테스트는 게이트웨이 관통 1발을 핵심으로 삼는다

`http://localhost:8080/master-service/actuator/health` 한 번이 네 가지를 동시에 단언한다.

1. 게이트웨이 라우트 매칭 (`Path=/master-service/**`)
2. `RewritePath` 접두사 제거
3. Eureka 레지스트리 조회 (`lb://master-service`) — 대상이 없으면 503
4. 업스트림 앱의 실제 응답

토큰이 필요 없는 것은 gateway `permit-all-paths`에 `/*-service/actuator/**`가 있기 때문이다.
이 덕분에 스모크 테스트가 auth-service(Optional)에 의존하지 않는다 — 만약 의존했다면
Core-only 범위 자체가 성립하지 않았다.

직접 확인(8종 published 포트)도 함께 하되, `compose --wait`가 이미 healthy를 보장한 뒤라
재시도를 짧게 잡는다. 관통 확인만 길게 재시도한다 — Eureka 조회 주기 5초와 게이트웨이
LB 캐시 TTL 5초 때문에 컨테이너가 healthy가 된 직후에도 라우팅은 잠시 503일 수 있다.

### 7. 무중단 배포를 하지 않는다 (명시적 수용)

단일 서버·단일 인스턴스라 blue-green이 성립하지 않는다. 재기동 중 수십 초 다운타임을
개발서버에서 수용한다. 이것을 「알려진 한계」 1에 적어 나중에 "왜 안 했지"가 다시 올라오지 않게 한다.

## 변경 내역

| 파일 | 변경 |
|---|---|
| `Jenkinsfile` | **신규** — 7단계 Declarative Pipeline + 실패 시 롤백·진단 수집 |
| `infrastructure/jenkins/deploy-core.ps1` | **신규** — backup / build / up / rollback / deploy. Jenkins 없이 손으로도 돌아간다 |
| `infrastructure/jenkins/smoke-test.ps1` | **신규** — 직접 8종 + 관통 2종. 수동 점검 도구로도 쓴다 |
| `infrastructure/docs/01-CICD-파이프라인-도입.md` | **신규** — 이 문서 |
| `infrastructure/docs/02-Jenkins-개발서버-구축절차.md` | **신규** — 손으로 따라하는 구축 절차 |
| `infrastructure/CLAUDE.md` | 도입부에 파이프라인 진입점 명시 + 상세 링크 |
| `infrastructure/docker/compose.yaml` | **변경 없음** — 결정 4의 결과. 롤백을 태그로 처리해 compose를 건드리지 않았다 |
| `docs/architecture/스마트정수장_리빌드_아키텍처.md` | **변경 없음** — CI/CD는 시스템 구조가 아니라 운영 절차다. 18장에 넣으면 같은 내용이 두 곳에 생긴다 |
| `.gitignore` | **변경 없음** — `build/` 무시가 이미 jar·이미지 산출물을 커밋에서 배제한다 |

## 구현 상세

### 파이프라인 7단계

```
① Checkout      GitHub master (github-swtp-pat) → 고정 workspace C:\swtp\ws
② Build         gradlew.bat --no-daemon clean build      ← Testcontainers 통합 테스트 포함
                post always: JUnit XML publish (실패해도 리포트는 남긴다)
③ Archive       apps/*/build/libs/*.jar 보관 (fingerprint)
④ Tag Backup    swtp-platform-*:latest → :prev
⑤ Image Build   compose build <Core 8종>
⑥ Deploy        compose up -d --wait --wait-timeout 600
⑦ Smoke         smoke-test.ps1 (직접 8 + 관통 2)
── post ──
   failure       BACKUP_DONE=true 일 때만 rollback. 아니면 "이미지 미변경, 스택 무손상" 보고
   unsuccessful  compose ps + logs --tail 300 을 build-diag/ 로 수집해 아티팩트로 보관
   always        docker image prune -f  (dangling만)
```

### 순서에 이유가 있는 지점

- **②와 ⑤를 분리한다.** 컴파일·테스트 실패를 이미지 빌드 **전에** 잡아 데몬에 쓰레기 레이어를 남기지 않는다.
- **④가 ⑤보다 앞이다.** `build`가 `:latest`를 덮어쓰기 전에 백업해야 한다. 순서를 바꾸면 롤백 대상이 사라진다.
- **`BACKUP_DONE` 플래그를 둔다.** ②에서 실패했는데 롤백을 돌리면 멀쩡한 스택을 이유 없이 재기동한다.
- **`prune`에 `-a`를 붙이지 않는다.** `-a`는 태그가 붙었어도 컨테이너가 참조하지 않는 이미지를 지운다 —
  `:prev`가 정확히 그 상태라 롤백 대상이 통째로 날아간다.

### `--no-daemon`을 쓰는 이유

Jenkins 서비스 계정에서 Gradle 데몬이 빌드 사이에 살아남으면, `gradle.properties`의
`org.gradle.configuration-cache=true`와 맞물려 이전 빌드 상태가 다음 빌드에 새어 든다.
온프레미스 단일 서버에서는 데몬 재사용으로 아끼는 시간보다 **매번 같은 상태에서 시작한다**가 값지다.

### `GRADLE_USER_HOME`을 고정하는 이유

서비스 계정의 홈 디렉토리는 대화형 로그인 계정과 다르다. 고정하지 않으면
"손으로 돌릴 때는 캐시가 있는데 Jenkins에서는 매번 의존성을 다시 받는" 상태가 조용히 만들어진다.

### 이미지 이름 유도 규칙

`compose.yaml`의 `name: swtp-platform`이 프로젝트 이름을 정하고, 빌드 산출 이미지는
`<프로젝트>-<서비스>:latest` 규칙으로 이름이 붙는다. `deploy-core.ps1`의 `$ProjectName`이
이 값을 복제하고 있으므로 **`compose.yaml`의 `name`을 바꾸면 스크립트도 함께 고쳐야 한다.**
안 고치면 백업·롤백이 아무 이미지도 못 찾고 조용히 빈손으로 성공한다.

## 함정 기록

1. **Docker Desktop은 서비스에서 안 보인다.**
   Windows용 Docker Desktop은 로그인한 사용자 세션에서 도는 프로그램이다. Jenkins가 `LocalSystem`으로
   돌면 `\\.\pipe\docker_engine`이 보이지 않아 `docker` 명령과 Testcontainers가 전부 실패한다.
   그런데 이 프로젝트는 통합 테스트를 우회할 수도 없다 — `build-logic`의 `verifyIntegrationTestBaseline`이
   `check`에 걸려 있고 루트 불변식 5가 H2 대체를 금지한다.
   **즉 "Jenkins 서비스 계정이 Docker에 닿느냐"가 이 프로젝트 CI의 생사 조건이다.** 절차는 02 문서 1장.
   *교훈: 통합 테스트가 실제 컨테이너를 띄우는 프로젝트에서 CI 설계의 첫 질문은 언어도 빌드 도구도 아니라 "빌드 주체가 Docker에 닿는가"다.*

2. **`--remove-orphans`를 쓰지 않는다.**
   계획 단계에서는 넣기로 했다가 구현하며 뺐다. `up -d --remove-orphans`를 프로파일 없이 실행하면
   compose가 **프로파일로 띄운 Optional 서비스(auth-service 등)를 고아로 판정해 제거한다.**
   Core만 배포한다는 결정이 "다른 사람이 띄워 둔 Optional을 조용히 내린다"로 번지는 것은 범위 위반이다.
   *교훈: 정리 옵션은 "무엇을 지우는가"가 아니라 "무엇을 남는 것으로 보는가"를 봐야 한다.*

3. **`docker image inspect`로 존재 확인을 하지 않는다.**
   이미지가 없을 때 stderr로 실패하는데, Windows PowerShell 5.1은 네이티브 명령의 stderr를
   `ErrorRecord`로 감싸 `$ErrorActionPreference = 'Stop'`과 충돌시킨다. 없으면 빈 문자열 +
   종료코드 0을 주는 `docker images -q`로 바꿨다.
   *교훈: PowerShell 스크립트에서 "실패가 정상 분기인 명령"은 종료코드로 판정하지 말고 무출력으로 판정한다.*

4. **Windows 포트 예약이 배포를 막을 수 있다.**
   `compose.no-publish.yaml`이 리포지토리에 존재하는 이유가 그 사고다 — Windows가 TCP 7981-8380을
   동적 예약하면 앱 포트(8080~8088) 게시가 거부된다. 개발서버도 Windows이므로 착수 전에
   `netsh interface ipv4 show excludedportrange protocol=tcp`로 확인해야 한다.
   *교훈: 이 리포지토리의 임시 파일들은 과거 사고의 화석이다. 새 환경에 나갈 때 먼저 읽을 것.*

5. **`ansiColor`를 넣지 않았다.**
   계획에는 있었으나 뺐다. 색상은 순전히 미관인데 플러그인이 없으면 파이프라인 자체가
   기동하지 않는다. 필수 플러그인 목록을 짧게 유지하는 편이 첫 구축 실패 지점을 줄인다.

## 알려진 한계

1. **개발서버 실측을 아직 하지 않았다.** 이 문서의 상태가 ✅가 아닌 이유다.
   스크립트와 Jenkinsfile은 정적으로만 검토됐다. 「검증」의 1~4단계를 개발서버에서 밟아야 한다.
2. **무중단 배포 없음.** 단일 인스턴스라 재기동 중 수십 초 다운타임이 있다.
3. **Core 8종 전량 재기동.** 한 서비스만 고쳐도 8종이 다시 뜬다. 변경 감지 기반 선택 배포는 「다음 단계」 1.
4. **Optional 4종·ELK·ai-service 미포함.** ai-service는 Python이라 빌드 경로가 아예 다르다.
5. **Docker Desktop 세션 의존.** 지정 계정이 로그아웃되면 CI 전체가 죽는다. 재부팅 후 자동 로그온이
   걸려 있지 않으면 조용히 실패한다. 벗어나려면 WSL2 Docker Engine 직결 또는 Jenkins의 리눅스 이전이고
   둘 다 이번 범위 밖이다.
6. **Jenkins가 공인IP에 노출된다.** Webhook을 받기 위한 대가다. 방화벽·인증 강화는 파이프라인 밖의
   전제조건이며 02 문서 3장에 적었다. 이 문서가 그 조치를 대신하지 않는다.
7. **미들웨어(TimescaleDB/Kafka)는 파이프라인이 재기동하지 않는다.** 배포마다 DB가 내려갈 위험을
   의도적으로 배제했다. 미들웨어 버전을 올릴 때는 손으로 한다.
8. **Flyway 마이그레이션 실패는 앱 기동 실패로만 드러난다.** 마이그레이션 자체를 되돌리는 절차는 없다.
   `:prev` 롤백은 **애플리케이션만** 되돌리므로, 스키마가 이미 앞으로 간 상태에서 구버전 앱이 뜨면
   또 다른 실패가 된다. 파괴적 마이그레이션을 쓰지 않는 것이 현재의 유일한 방어다.

## 검증

개발서버에서 아래 순서로 밟는다. **1단계가 통과하지 않으면 파이프라인은 의미가 없다** —
파이프라인은 이 명령들을 대신 칠 뿐이기 때문이다.

### 1단계 — Jenkins 없이 손으로

```powershell
cd C:\swtp\ws
.\gradlew.bat --no-daemon clean build
.\infrastructure\jenkins\deploy-core.ps1 -Action deploy
.\infrastructure\jenkins\smoke-test.ps1
```

확인 항목:
1. 통합 테스트 통과 (Testcontainers가 PostgreSQL 컨테이너를 실제로 띄웠는가)
2. 8080·8081·8082·8083·8084·8087·8761·8888 게시 성공 (포트 예약에 걸리지 않았는가)
3. `docker compose ps`에서 Core 8종 전부 `healthy`
4. `logs/<서비스>/` 디렉토리 생성
5. config-server가 마운트된 `config-repo`를 서빙 — `curl http://localhost:8888/application/docker`
6. 스모크 10건 전부 `[OK]`

### 2단계 — Jenkins job 수동 실행

`Build Now` → 7단계 전부 통과. JUnit 리포트와 jar 아티팩트가 빌드 페이지에 보이는지 확인.

### 3단계 — Webhook 자동 트리거

master에 커밋 푸시 → GitHub `Settings → Webhooks → Recent Deliveries`가 200 → Jenkins 자동 시작.

### 4단계 — 실패 주입 (여기까지 해야 파이프라인을 신뢰할 수 있다)

| 주입 | 기대 결과 |
|---|---|
| 컴파일 에러 커밋 | ②에서 실패. ④⑤⑥ 미실행, 롤백도 미실행("이미지 미변경" 메시지), 기존 스택 무손상 |
| 테스트 1건 고의 실패 | ②에서 실패하되 JUnit 리포트에 실패 테스트가 표시됨. 배포 미실행 |
| health가 안 뜨는 코드 배포 | ⑥ `--wait` 타임아웃 → post에서 `:prev` 롤백 → 이전 버전으로 healthy 복귀. `build-diag/` 아티팩트에 컨테이너 로그가 남는지 확인 |

### 5단계 — 문서 재현성

02 절차서를 **처음부터 그대로 따라** 재현 가능한지 확인한다.

### 실측 결과

**미실행.** 개발서버에서 1~4단계를 밟은 뒤 각 명령의 출력을 여기에 **원문 그대로** 기록한다.
읽기 좋게 재배열하지 않는다 — 순서와 실행시간을 손대면 원본과 대조할 수 없게 된다.

## 다음 단계

1. **변경 감지 기반 선택 빌드** — `git diff --name-only`로 바뀐 모듈만 빌드·재기동.
   선행 조건: 1차 파이프라인이 안정적으로 돌아 "전량 재기동이 실제로 병목인가"가 측정된 뒤.
2. **빌드번호 이미지 태그** — `compose.yaml`에 `image: swtp/<name>:${SWTP_IMAGE_TAG}` 추가 →
   N단계 롤백. 선행 조건: 결정 4의 `:prev` 방식이 한 번 이상 실제로 롤백에 쓰여 본 뒤.
3. **Optional 4종 + ai-service 확장** — ai-service는 Python이라 별도 빌드 단계가 필요하다.
   선행 조건: Core 파이프라인 안정화.
4. **정수장 현장 서버 배포** — 여기서 처음으로 레지스트리 또는 `docker save`/`load` 반출 절차가
   필요해진다. 결정 1을 재검토할 유일한 시점이다.
5. **Flyway 실패 대응 절차** — 한계 8을 메운다. 선행 조건: 파괴적 마이그레이션이 실제로 필요해지는 시점.
