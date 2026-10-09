# 02 — Jenkins 개발서버 구축 절차 (손으로 따라하는 순서)

- 일자: 2026-08-24
- 상태: 🔍 논의 → 작성 완료, **개발서버 실행 미확인**
- 대상: 개발서버(Windows)에서 Jenkins를 운영하는 사람
- 설계 근거: `infrastructure/docs/01-CICD-파이프라인-도입.md` — **왜 이렇게 하는가**는 그쪽에 있다. 이 문서는 **무엇을 어떤 순서로 하는가**만 담는다.

---

## 0. 먼저 — 전체 그림

"GitHub에 푸시하면 개발서버에 반영된다"는 실제로 네 단계다.

```
  개발 PC                    GitHub                     개발서버 (Windows)
  ───────                    ──────                     ──────────────────
  git push master  ────►  master 갱신
                             │
                             │ ② Webhook (HTTP POST)
                             ▼
                          Jenkins ◄──── ③ git clone/pull ────  소스가 C:\swtp\ws 에 내려온다
                             │
                             │ ④ gradlew build      → jar 생성
                             │ ⑤ docker compose build → 이미지 생성 (같은 서버의 Docker 데몬 안)
                             │ ⑥ docker compose up -d → 컨테이너 교체
                             │ ⑦ 스모크 테스트       → 실패하면 이전 이미지로 되돌림
                             ▼
                          Core 8종 가동
```

**"이미지를 어디에 보관하느냐"는 질문이 여기서는 사라진다.** 이미지를 만드는 기계(Jenkins)와
실행하는 기계(앱 컨테이너)가 **같은 서버의 같은 Docker 데몬**이기 때문이다.
`docker compose build`가 끝난 순간 이미지는 이미 실행될 자리에 있다. 레지스트리(창고)는
"만든 곳과 쓰는 곳이 다를 때" 필요한 물건이고, 그건 나중에 정수장 현장 서버로 나갈 때 이야기다.

### 이 문서에서 하는 일

| 장 | 내용 | 대략 소요 |
|---|---|---|
| 1 | Jenkins가 Docker를 쓸 수 있게 만든다 ★ 가장 중요 | 30분 |
| 2 | 개발서버 사전 점검 (Java·Docker·포트·디스크) | 20분 |
| 3 | GitHub 준비 (PAT 발급, Webhook 등록) | 20분 |
| 4 | Jenkins 준비 (플러그인, 인증정보, URL, 보안) | 30분 |
| 5 | 배포 디렉토리와 `.env` 배치 | 10분 |
| 6 | **Jenkins 없이 손으로 1회 배포** — 여기가 진짜 관문 | 1~2시간 (첫 이미지 pull 포함) |
| 7 | Jenkins job 생성 | 15분 |
| 8 | 첫 자동 배포 확인 | 30분 |
| 9 | 실패 주입 테스트 | 30분 |

6장이 통과하지 않으면 7장 이후는 무의미하다. 파이프라인은 6장의 명령을 대신 칠 뿐이다.

---

## 1. Jenkins가 Docker를 쓸 수 있게 만든다 ★

### 왜 이게 첫 번째인가

Windows용 Docker Desktop은 **로그인한 사용자 세션에서 도는 프로그램**이다.
Jenkins가 `LocalSystem` 계정으로 도는 서비스라면 Docker의 명명 파이프(`\\.\pipe\docker_engine`)가
보이지 않아 `docker` 명령이 통째로 실패한다.

그리고 이 프로젝트는 그 실패를 우회할 수 없다. 통합 테스트가 Testcontainers로
**실제 PostgreSQL 컨테이너를 띄우고**, `build-logic`의 `verifyIntegrationTestBaseline`이
`check` 단계에 걸려 있어 테스트를 빼는 것도 빌드 실패로 막혀 있다(루트 불변식 5: H2 금지).

**즉 이 관문을 넘지 못하면 CI가 성립하지 않는다.**

### 1-1. 현재 상태 확인

관리자 PowerShell에서:

```powershell
# Jenkins 서비스가 어떤 계정으로 도는지
Get-CimInstance Win32_Service -Filter "Name='Jenkins'" | Select-Object Name, StartName, State

# Docker 데몬이 살아 있는지 (대화형 계정 기준)
docker version
```

`StartName`이 `LocalSystem`이면 아래 1-2를 반드시 해야 한다.
이미 사람 계정(`.\사용자명` 또는 `도메인\사용자명`)이면 1-3부터 확인한다.

### 1-2. Jenkins 서비스 계정 전환

Docker Desktop을 쓰는 계정을 `<DOCKER계정>`이라 하자.

**① `docker-users` 그룹에 추가** — 관리자 PowerShell

```powershell
net localgroup docker-users <DOCKER계정> /add
net localgroup docker-users            # 추가됐는지 확인
```

**② "서비스로 로그온" 권한 부여**

1. `Win + R` → `secpol.msc`
2. 로컬 정책 → 사용자 권한 할당
3. **서비스로 로그온** 더블클릭 → 사용자 또는 그룹 추가 → `<DOCKER계정>` 추가 → 확인

**③ Jenkins 서비스의 로그온 계정 변경**

1. `Win + R` → `services.msc`
2. `Jenkins` 더블클릭 → **로그온** 탭
3. **다음 계정으로 로그온** 선택 → `<DOCKER계정>` + 비밀번호 입력 → 확인
4. 서비스 마우스 오른쪽 → **다시 시작**

**④ Docker Desktop 자동 시작**

Docker Desktop → Settings → General → **Start Docker Desktop when you log in** 체크

**⑤ 서버 자동 로그온** (보안 정책이 허용하는 경우)

Docker Desktop이 세션에 묶여 있으므로, 재부팅 후 아무도 로그인하지 않으면 CI가 죽는다.
`netplwiz` 또는 Sysinternals `Autologon`으로 `<DOCKER계정>` 자동 로그온을 설정한다.

> 사내 보안 정책상 자동 로그온이 불가하다면, **재부팅 후 그 계정으로 한 번 로그인해야
> CI가 산다**는 사실을 운영 수칙으로 남긴다. 이 제약을 벗어나려면 WSL2에 Docker Engine을
> 직접 설치하는 방향(01 문서 「알려진 한계」 5)이 있으나 이번 범위 밖이다.

### 1-3. Jenkins에서 Docker가 실제로 보이는지 검증

Jenkins → 새로운 Item → 이름 `docker-check` → **Freestyle project** → OK
→ Build Steps → **Windows PowerShell** 추가 → 아래 입력 → 저장 → **Build Now**

```powershell
whoami
docker version
docker ps
```

**Console Output에 Docker `Server:` 블록이 찍히면 통과다.**
`error during connect` / `The system cannot find the file specified` 가 나오면 1-2로 돌아간다.

- [ ] `docker version`이 Server 정보까지 출력됨

---

## 2. 개발서버 사전 점검

관리자 PowerShell에서:

```powershell
java -version                                              # 21 이어야 한다
docker version
docker compose version
netsh interface ipv4 show excludedportrange protocol=tcp    # ★ 8080~8088 예약 여부
Get-Volume C | Select-Object DriveLetter, SizeRemaining
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory
```

### 2-1. 포트 예약 확인 ★

이 리포지토리에 `infrastructure/docker/compose.no-publish.yaml`이 있는 이유가 이 사고다 —
Windows가 TCP 7981-8380 대역을 동적 예약하면 앱 포트(8080~8088) 게시가 거부된다.

출력 범위에 **8080~8088, 8761, 8888이 포함되면** 관리자 권한으로:

```powershell
net stop winnat
net start winnat
netsh interface ipv4 show excludedportrange protocol=tcp    # 다시 확인
```

해소되지 않으면 배포 자체가 실패한다. 착수 전에 반드시 정리한다.

### 2-2. 자원

- 디스크 여유 **40GB 이상** 권장 (베이스 이미지 + 앱 이미지 8종 + `:prev` 백업 + Gradle 캐시)
- 메모리 **16GB 이상** 권장 (Core 8종 + TimescaleDB + Kafka. ELK까지 켜면 더 필요)

### 2-3. 베이스 이미지 미리 받기

첫 빌드가 이미지 pull만으로 수십 분 걸려 타임아웃에 걸리는 일을 막는다.

```powershell
docker pull eclipse-temurin:21-jre
docker pull timescale/timescaledb-ha:pg17
docker pull apache/kafka:4.3.1
docker pull testcontainers/ryuk:0.11.0
```

> `testcontainers/ryuk` 태그는 Testcontainers 버전에 따라 다르다. 정확한 태그는 첫 통합 테스트
> 실행 로그에서 확인하고, 그때 받아도 된다.

- [ ] Java 21 확인
- [ ] Docker 데몬 응답 확인
- [ ] 8080~8088 / 8761 / 8888 포트 예약 없음
- [ ] 디스크 40GB 이상
- [ ] 베이스 이미지 pull 완료

---

## 3. GitHub 준비

### 3-1. Personal Access Token 발급

1. GitHub → 우상단 프로필 → Settings → Developer settings
2. Personal access tokens → **Fine-grained tokens** → Generate new token
3. 설정
   - Token name: `jenkins-swtp-dev`
   - Expiration: 조직 정책에 맞게 (만료일을 캘린더에 적어 둘 것 — 만료되면 CI가 조용히 멈춘다)
   - Repository access: **Only select repositories** → `mindonecode/swtp_platform`
   - Permissions → Repository permissions → **Contents: Read-only**
4. Generate → **토큰 문자열을 즉시 복사** (다시 볼 수 없다)

> 조직 리포지토리라면 조직 Settings에서 fine-grained PAT 사용이 **허용**돼 있어야 한다.
> 막혀 있으면 조직 관리자에게 승인을 요청하거나 classic PAT(`repo` 스코프)로 대체한다.

### 3-2. Webhook 등록

Jenkins 쪽 설정(4장)을 마친 뒤에 하는 편이 낫지만, 값은 지금 정해 둔다.

1. 리포지토리 → Settings → Webhooks → **Add webhook**
2. 설정
   - **Payload URL**: `http://<개발서버 공인IP>:<Jenkins포트>/github-webhook/`
     - **끝 슬래시가 필수다.** 빠지면 404가 나면서 트리거가 안 걸린다.
   - **Content type**: `application/json`
   - **Secret**: 임의의 긴 문자열을 만들어 넣고 **따로 보관** (4장에서 Jenkins에도 같은 값을 넣는다)
   - **Which events**: `Just the push event`
   - Active 체크
3. Add webhook

- [ ] PAT 발급 및 보관
- [ ] Webhook 등록 (URL 끝 슬래시 확인)
- [ ] Webhook Secret 보관

---

## 4. Jenkins 준비

### 4-1. 플러그인 설치

Manage Jenkins → Plugins → Available plugins 에서 설치:

| 플러그인 | 용도 |
|---|---|
| Pipeline | Declarative Pipeline 실행 (보통 기본 설치) |
| Git | SCM checkout |
| GitHub | `githubPush()` 트리거, Webhook 수신 |
| Credentials Binding | 인증정보 주입 |
| JUnit | 테스트 리포트 표시 |
| Timestamper | 로그 타임스탬프 (`timestamps()` 옵션이 요구) |
| Build Timeout | `timeout()` 옵션 |

> `Jenkinsfile`의 `options`에 있는 `timestamps()`가 Timestamper를, `timeout()`이 Build Timeout을
> 요구한다. 없으면 파이프라인이 **첫 줄에서** 실패하므로 빌드 로그가 아니라 "설정 오류"로 보인다.

설치 후 Jenkins 재시작.

### 4-2. 인증정보 등록

Manage Jenkins → Credentials → System → Global credentials → **Add Credentials**

**① GitHub PAT**

| 항목 | 값 |
|---|---|
| Kind | Username with password |
| Username | GitHub 계정명 |
| Password | 3-1에서 받은 PAT |
| ID | `github-swtp-pat` ← **정확히 이 문자열** (7장에서 참조한다) |
| Description | GitHub swtp_platform 읽기 전용 |

**② Webhook Secret**

| 항목 | 값 |
|---|---|
| Kind | Secret text |
| Secret | 3-2에서 정한 Secret |
| ID | `github-webhook-secret` |

### 4-3. Jenkins URL 설정

Manage Jenkins → System → **Jenkins Location** → Jenkins URL 을
`http://<개발서버 공인IP>:<Jenkins포트>/` 로 정확히 입력 → 저장.

이 값이 틀리면 Webhook 검증과 GitHub 연동이 조용히 어긋난다.

### 4-4. GitHub Webhook Secret 연결

Manage Jenkins → System → **GitHub** 섹션 → Advanced →
**Shared secrets**에 `github-webhook-secret` 지정 → 저장.

### 4-5. 보안 — 건너뛰지 말 것

공인IP로 열리는 순간 Jenkins는 무단 접속 시도의 대상이 된다. 최소한:

- Manage Jenkins → Security → **익명 읽기 권한 해제**, 로그인한 사용자만 접근
- 관리자 비밀번호를 충분히 길게
- 방화벽에서 Jenkins 포트를 **GitHub Webhook 발신 IP 대역 + 사내 IP**로만 제한
  - 대역은 `https://api.github.com/meta` 응답의 `hooks` 배열에 있다 (주기적으로 바뀐다)
- 가능하면 HTTPS 역프록시(IIS / nginx)를 앞에 두고 평문 HTTP 노출을 없앤다

> 이 조치는 파이프라인 밖의 전제조건이다. 01 문서가 이 위험을 「알려진 한계」 6으로 기록해 두었다.

- [ ] 플러그인 7종 설치 및 재시작
- [ ] `github-swtp-pat` 등록
- [ ] `github-webhook-secret` 등록
- [ ] Jenkins URL 설정
- [ ] 익명 접근 차단 + 방화벽 제한

---

## 5. 배포 디렉토리와 `.env` 배치

### 5-1. 디렉토리 준비

```powershell
New-Item -ItemType Directory -Force -Path C:\swtp\ws
New-Item -ItemType Directory -Force -Path C:\swtp\gradle-home
```

- `C:\swtp\ws` — Jenkins workspace **겸** 배포 루트. `Jenkinsfile`의 `customWorkspace`가 이 경로다.
- `C:\swtp\gradle-home` — `GRADLE_USER_HOME`. 서비스 계정 홈이 대화형 계정과 달라 고정한다.

> **이 workspace는 절대 지우지 않는다.** `compose.yaml`이 `../../config-repo`와 `../../logs`를
> 컨테이너에 bind mount 하므로, 떠 있는 컨테이너가 이 디렉토리를 런타임 내내 읽고 쓴다.
> 청소하면 살아 있는 스택이 깨진다. (근거: 01 문서 「결정 3」)

두 경로 모두 `<DOCKER계정>`이 읽고 쓸 수 있어야 한다.

### 5-2. 최초 clone

```powershell
cd C:\swtp
git clone https://github.com/mindonecode/swtp_platform.git ws
```

> 인증을 물으면 GitHub 계정 + PAT를 넣는다. 이후에는 Jenkins가 자기 인증정보로 checkout하므로
> 이 clone은 디렉토리를 만드는 용도다.

### 5-3. `.env` 배치

`.env`는 `.gitignore` 대상이라 **파이프라인이 만들지 않는다.** 한 번 손으로 놓아야 한다.

```powershell
cd C:\swtp\ws\infrastructure\docker
Copy-Item .env.example .env
notepad .env
```

개발서버 값:

```dotenv
SWTP_PROFILES=docker
SWTP_LOG_FORMAT=plain
SWTP_SITE_CODE=DEV-01
```

- `SWTP_LOG_FORMAT` — ELK(`--profile elk`)를 운용할 계획이면 `ecs`로 둔다.
  `plain`이면 수집은 되되 Kibana에서 필드 검색이 성립하지 않는다.
- `SWTP_SITE_CODE` — 비워 두면 로그·메트릭에 사이트 식별자가 없이 적재된다.
  나중에 중앙 집계로 전환할 때 이미 쌓인 인덱스를 재색인해야 하므로 지금 채운다.

- [ ] `C:\swtp\ws`, `C:\swtp\gradle-home` 생성
- [ ] clone 완료
- [ ] `.env` 배치 및 `SWTP_SITE_CODE` 입력

---

## 6. Jenkins 없이 손으로 1회 배포 ★ 진짜 관문

**여기가 통과하지 않으면 7장 이후는 의미가 없다.** 파이프라인은 아래 명령을 대신 칠 뿐이다.

`<DOCKER계정>`으로 로그인한 상태에서, PowerShell:

```powershell
cd C:\swtp\ws
$env:GRADLE_USER_HOME = 'C:\swtp\gradle-home'

# ① Gradle 빌드 — 통합 테스트 포함. 첫 실행은 20~40분 걸릴 수 있다.
.\gradlew.bat --no-daemon --stacktrace clean build

# ② 이미지 빌드 + 기동 (백업 → 빌드 → up --wait 를 한 번에)
.\infrastructure\jenkins\deploy-core.ps1 -Action deploy

# ③ 스모크 테스트
.\infrastructure\jenkins\smoke-test.ps1
```

### 확인 항목

```powershell
docker compose -f infrastructure\docker\compose.yaml ps
```

1. Core 8종이 전부 `healthy`
   (config-server, discovery-server, gateway, master/telemetry/realtime/job/ems-service)
2. `logs\<서비스>\` 디렉토리가 생성됨
3. config-server가 마운트된 `config-repo`를 서빙:
   ```powershell
   Invoke-RestMethod http://localhost:8888/application/docker
   ```
4. Eureka 대시보드에 인스턴스가 보임: 브라우저로 `http://localhost:8761`
5. 게이트웨이 관통:
   ```powershell
   Invoke-RestMethod http://localhost:8080/master-service/actuator/health
   ```
6. 스모크 테스트 10건 전부 `[OK]`

- [ ] `gradlew build` 통과 (통합 테스트 포함)
- [ ] Core 8종 healthy
- [ ] 스모크 테스트 전건 통과

---

## 7. Jenkins job 생성

1. Jenkins → **새로운 Item**
2. 이름: `swtp-platform-dev`
3. **Pipeline** 선택 → OK
4. 설정

| 항목 | 값 |
|---|---|
| **Build Triggers** | ☑ GitHub hook trigger for GITScm polling |
| **Pipeline → Definition** | `Pipeline script from SCM` |
| SCM | Git |
| Repository URL | `https://github.com/mindonecode/swtp_platform.git` |
| Credentials | `github-swtp-pat` |
| Branch Specifier | `*/master` |
| Script Path | `Jenkinsfile` |
| ☐ Lightweight checkout | **해제** (workspace에 실제 소스가 있어야 한다) |

5. 저장

> `Jenkinsfile`이 `customWorkspace 'C:\swtp\ws'`를 지정하므로 job 이름과 무관하게
> 5장에서 만든 디렉토리를 쓴다. **job 이름을 바꿔도 workspace는 그대로다** — 의도한 설계다.

- [ ] job 생성 및 SCM 설정
- [ ] Lightweight checkout 해제 확인

---

## 8. 첫 자동 배포 확인

### 8-1. 수동 실행

**Build Now** → Console Output에서 7단계가 순서대로 지나가는지 확인.

빌드 페이지에서 확인:
- **Test Result** — JUnit 리포트가 보인다
- **Build Artifacts** — `apps/*/build/libs/*.jar` 가 보인다

### 8-2. Webhook 자동 트리거

개발 PC에서 master에 아무 커밋이나 푸시한다(README 한 줄이면 충분하다).

```bash
git commit --allow-empty -m "chore) CI 트리거 확인"
git push origin master
```

확인:
1. GitHub → Settings → Webhooks → 해당 훅 → **Recent Deliveries** 가 초록 체크(200)
2. Jenkins에서 빌드가 자동으로 시작됨

- [ ] Build Now 전 단계 통과
- [ ] Webhook Delivery 200
- [ ] 푸시로 빌드 자동 시작

---

## 9. 실패 주입 테스트

**여기까지 해야 파이프라인을 신뢰할 수 있다.** "성공할 때 잘 돈다"는 절반의 정보다.

| # | 주입 방법 | 기대 결과 |
|---|---|---|
| 1 | 아무 앱의 java 파일에 문법 오류를 넣고 푸시 | 2단계에서 실패. 4~6단계 미실행. Console에 *"빌드 단계에서 실패했다 — 이미지를 건드리지 않았으므로 기존 스택은 무손상이다"*. `docker compose ps`로 스택이 그대로인지 확인 |
| 2 | 테스트 하나에 `fail()` 추가 후 푸시 | 2단계 실패 + **Test Result에 실패 테스트가 표시됨**. 배포 미실행 |
| 3 | 앱 하나의 health가 안 뜨게 만들고 푸시 (예: 잘못된 DB URL) | 6단계 `--wait` 타임아웃 → post에서 `:prev` 롤백 실행 → 이전 버전으로 healthy 복귀. **Build Artifacts에 `build-diag/compose-logs.txt`가 생겼는지 확인** |

3번을 마친 뒤에는 반드시 정상 커밋을 푸시해 스택을 최신으로 되돌린다.

- [ ] 컴파일 실패 시 스택 무손상 확인
- [ ] 테스트 실패가 리포트에 표시됨
- [ ] health 실패 시 롤백 동작 + 진단 로그 아티팩트 생성

---

## 10. 일상 운영

### 수동 배포 (Jenkins가 멈췄을 때)

```powershell
cd C:\swtp\ws
git pull
.\gradlew.bat --no-daemon clean build
.\infrastructure\jenkins\deploy-core.ps1 -Action deploy
.\infrastructure\jenkins\smoke-test.ps1
```

### 수동 롤백

```powershell
.\infrastructure\jenkins\deploy-core.ps1 -Action rollback
```

`:prev` 태그가 붙은 **직전 1개 버전**으로만 되돌아간다. 두 단계 전으로는 갈 수 없다.

### 상태 확인

```powershell
cd C:\swtp\ws
docker compose -f infrastructure\docker\compose.yaml ps
docker compose -f infrastructure\docker\compose.yaml logs -f --tail 100 gateway
.\infrastructure\jenkins\smoke-test.ps1
```

### 로그 파일

`C:\swtp\ws\logs\<서비스>\` — 컨테이너의 `/logs`가 여기로 bind mount 돼 있다.
감사·장애분석용 평문 백업이며, 로그 수집기(ELK)는 이 디렉토리를 읽지 않는다(stdout 단독, 이중 적재 방지).

### 디스크 관리

파이프라인이 매 빌드 후 `docker image prune -f`로 dangling 이미지를 지운다.
그래도 쌓이면 수동으로:

```powershell
docker system df                 # 무엇이 얼마나 먹는지 먼저 본다
docker builder prune -f          # 빌드 캐시 정리
```

> **`docker image prune -a` 는 쓰지 말 것.** `:prev` 태그가 붙은 롤백 대상까지 지운다.

---

## 부록 A. 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| `error during connect: ... \\.\pipe\docker_engine` | Jenkins 서비스 계정이 Docker Desktop 세션 밖 | 1장 전체 재확인. 재부팅 후라면 `<DOCKER계정>` 로그인 여부부터 |
| 통합 테스트가 `Could not find a valid Docker environment` | 같은 원인 | 1-3 검증 job으로 먼저 확인 |
| `Ports are not available: ... bind: An attempt was made to access a socket...` | Windows 포트 예약(winnat) | 2-1. `net stop winnat; net start winnat` |
| 푸시해도 빌드가 안 걸림 | Webhook URL 끝 슬래시 누락 / Jenkins URL 오설정 / 방화벽 | GitHub Recent Deliveries의 응답 코드부터 본다. 404면 URL, 타임아웃이면 방화벽 |
| `checkout` 에서 인증 실패 | PAT 만료 또는 조직의 fine-grained PAT 차단 | 3-1 재발급. 조직 정책 확인 |
| 6단계 `--wait` 타임아웃 | 앱이 실제로 안 뜸 | `build-diag/compose-logs.txt` 아티팩트를 먼저 읽는다. Flyway 실패·config-server 미연결이 흔하다 |
| 스모크 관통 확인만 503 | Eureka 등록 전파 지연 | 정상 범위일 수 있다(재시도 12회×5초). 계속 503이면 `http://localhost:8761`에서 인스턴스 등록 여부 확인 |
| Gradle이 매번 의존성을 다시 받음 | `GRADLE_USER_HOME` 미적용 | Jenkins job 로그에서 환경변수 확인. 5-1 디렉토리 권한 확인 |
| 롤백이 *"복원할 :prev 이미지가 하나도 없다"* | 최초 배포 실패 | 정상 동작이다. 되돌릴 곳이 없으므로 원인을 고치고 다시 배포한다 |
| 파이프라인이 첫 줄에서 실패 | 플러그인 누락(`timestamps`/`timeout`/`githubPush`) | 4-1 플러그인 7종 확인 |

## 부록 B. 최종 체크리스트

```
[ ] 1  Jenkins 서비스 계정이 Docker에 닿는다 (docker-check job 통과)
[ ] 2  Java 21 / Docker / 포트 예약 없음 / 디스크 40GB
[ ] 3  GitHub PAT 발급 + Webhook 등록 (끝 슬래시)
[ ] 4  플러그인 7종 + 인증정보 2건 + Jenkins URL + 보안 조치
[ ] 5  C:\swtp\ws clone + .env 배치 (SWTP_SITE_CODE 입력)
[ ] 6  손으로 1회 배포 성공 + 스모크 10건 통과      ← 진짜 관문
[ ] 7  Jenkins job 생성 (Lightweight checkout 해제)
[ ] 8  Build Now 통과 + Webhook 자동 트리거 확인
[ ] 9  실패 주입 3종 확인 (무손상 / 리포트 / 롤백)
```

## 부록 C. 이 문서 밖의 일

- **Optional 4종(auth·autonomous·pms) + ai-service 배포** — 1차 범위 밖. 01 문서 「다음 단계」 3
- **ELK 관측 스택** — `--profile elk`로 별도 기동. `infrastructure/CLAUDE.md`「관측 백엔드」
- **정수장 현장 서버 배포** — 여기서 처음으로 이미지 반출 절차가 필요해진다. 01 문서 「다음 단계」 4
