# Step 19 — 앱마다 CLAUDE.md를 둔다

- 일자: 2026-08-21
- 상태: ✅ 완료 (앱 11종 전부 모듈 문서 보유. 스캐폴드 3종은 계약 기반 작성)
- 관련: [sync-01](../../sync/sync-01-20260821.md) D5, [step-18](step-18-gateway-route-convention.md)

## 발단

sync-01의 D5는 "`apps/master-service`가 08-14 이후 최대 변경처(35파일)인데 모듈 문서가 없다"였다. 당시 스킬은 **문서를 자동 생성하지 않고 공백만 보고**했다 — 코드를 읽으면 아는 내용만 담긴 빈 껍데기가 늘면 CLAUDE.md 전체의 신뢰가 떨어진다는 이유였다.

그 판단의 전제는 "규모가 커진 모듈에만 문서가 필요하다"였는데, **기준이 틀렸다.** 실제로 문서를 갖고 있던 3종(auth·gateway·job)의 공통점은 규모가 아니라 **"코드가 답하지 못하는 규약이 있다"** 였고, 그 기준으로 보면 소스 1개짜리 `config-server`가 소스 25개짜리 `master-service`보다 문서가 더 급했다.

## 결정 (2026-08-21)

### 1. 앱 11종이 전부 자기 `CLAUDE.md`를 갖는다

서비스마다 **역할이 다르기 때문**이다. 역할·경계·금지사항은 코드를 훑어서 나오지 않는다 — "이 서비스가 무엇을 **하지 않는가**"는 어느 파일에도 적혀 있지 않다.

신설 8종: `master-service`, `telemetry-service`, `realtime-service`, `config-server`, `discovery-server`, `ems-service`, `pms-service`, `autonomous-service`. 기존 3종(`auth-service`, `gateway`, `job-service`)은 그대로 둔다.

### 2. 층 분리 기준은 "여러 앱에 걸치는가"

문서를 11개로 늘리면 **가장 큰 위험은 중복**이다. 포트 표가 11번 복제되면 갈라지고, 갈라진 것을 발견할 방법이 없다. 그래서 기준을 `apps/CLAUDE.md`에 먼저 못박았다.

| `apps/CLAUDE.md` | 앱별 `CLAUDE.md` |
|---|---|
| 앱들을 **비교**해야 아는 것 (포트·스키마 표, 스타터 조합, 통합 테스트 표준형) | 그 앱 **하나만** 아는 것 (역할과 경계, 자기 설정의 이유, 자기 함정) |

**포트·소유 스키마·배포 구분은 앱별 문서에 다시 적지 않는다.** 실제로 8종을 쓰면서 이 규칙 하나가 각 문서를 절반 이하로 줄였다.

### 3. 스캐폴드 서비스일수록 문서 가치가 크다

`ems`·`pms`·`autonomous`는 `*Application.java` 한 개뿐이다. 그런데 **계약은 이미 존재한다** — 아키텍처 4.2~4.4가 역할을, `01-schemas.sql`이 소유 스키마를, `create-topics.sh`가 발행할 토픽을 이미 정해 뒀다. 코드가 비어 있을 뿐 설계는 비어 있지 않다.

그래서 이 셋의 문서는 "지금 무엇이 있는가"가 아니라 **"구현할 때 무엇을 지켜야 하는가"** 를 담는다. 구현이 들어오는 시점이 규약을 가장 쉽게 어기는 시점이다.

**기각한 대안 — 구현 후 작성.** 그때는 이미 결정이 내려진 뒤라 문서가 사후 정당화가 된다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `apps/{master,telemetry,realtime}-service/CLAUDE.md` | 신설 — 실제 구현이 있는 3종 |
| `apps/{config-server,discovery-server}/CLAUDE.md` | 신설 — 소스는 1개지만 규약이 조밀한 인프라 2종 |
| `apps/{ems,pms,autonomous}-service/CLAUDE.md` | 신설 — 계약 기반 3종 |
| `apps/CLAUDE.md` | "모듈 문서" 절 추가 — 층 분리 기준과 중복 금지 |
| 루트 `CLAUDE.md` | **변경 없음** — 불변식이 바뀌지 않았다(갱신 규칙) |
| `docs/sync/주장-대조표.md` | 앱별 문서 존재 명제 + 작업 중 발견한 드리프트 2건 추가 |

---

## 함정 기록

1. **`master-service`만 `spring.profiles.active: dev`를 자기 yml에 박고 있다.** 11개 앱 중 유일하다. 로컬 `bootRun`에서는 config-server가 `config-repo/application-dev.yml`을 함께 내려주고 그 파일이 `swtp.auth.mode: none`이라 **인증이 꺼진 채로 뜬다.** 컨테이너는 compose의 `SPRING_PROFILES_ACTIVE=docker`가 덮어 재현되지 않는다 — **로컬에서 통과하고 배포하면 401이 나는 조합**이 성립한다. step-18에서 새로 쓴 규칙("한 키를 두 축이 함께 건드리지 않는다")이 정확히 가리키는 상태다. 이번에는 문서화만 하고 손대지 않았다(한계 #2).

2. **`ems`·`pms` 스키마는 이미 만들어져 있는데 모듈에는 DB 의존이 없다.** `01-schemas.sql`이 `-- ems-service 소유` 주석까지 달아 생성하고 `apps/CLAUDE.md` 표도 소유 스키마를 적어 뒀지만, 두 모듈 어디에도 `swtp-persistence-starter`가 없다. **표가 코드보다 앞서 있는 상태**다 — 틀린 것은 아니지만 "소유한다"는 서술이 현재형이라 오해를 부른다. 각 모듈 문서에 "DB는 자리를 비워 두고 기다리는 상태"라고 명시했다.

3. **`master-service`는 실행 가능한 테스트가 0개다.** `failOnNoDiscoveredTests = false`로 빌드 가드를 꺼 두었다. sample 폐기의 잔여물이고, 남은 `AbstractIntegrationTest`는 `apps/CLAUDE.md`가 경로까지 지목한 추상 기준형이라 지울 수 없었다. 도메인 테스트가 들어오면 이 블록을 지워야 한다 — 남겨 두면 **진짜로 테스트가 사라진 순간을 빌드가 알려주지 못한다.**

4. **문서를 11개로 늘리는 작업의 실패 모드는 "빈 껍데기"가 아니라 "중복"이었다.** 초안을 쓰다 보면 각 문서가 포트·스키마·스타터 조합을 자기 안에 다시 적으려 한다. 층 분리 기준을 먼저 정하지 않았다면 8개 파일에 같은 표가 8번 들어갔을 것이다.

## 알려진 한계

1. **스캐폴드 3종의 문서는 아키텍처 계약에 기반한다.** 구현이 들어오면 실제 결정과 어긋날 수 있다 — 그때 재작성 대상이다.

2. **`master-service`의 `dev` 프로파일을 손대지 않았다.** 함정 #1이 남아 있다. 의도적 개발 편의일 수 있어 임의로 지우지 않았다 — 제거하거나, `config-repo/application-dev.yml`의 `swtp.auth.mode: none`을 `${SWTP_AUTH_MODE:none}`으로 바꿔 환경변수 우선을 지키는 두 방향이 있다(step-18 한계 #3과 같은 뿌리다).

3. **sync-01의 D6(`AbstractIntegrationTest` 5/11)은 여전히 미결이다.** 이번 작업에서 실측을 다시 확인했을 뿐 결정하지 않았다.

## 검증

```bash
for d in apps/*/; do [ -f "$d/CLAUDE.md" ] && echo "O $(basename $d)" || echo "- $(basename $d)"; done
grep -l '| 80[0-9][0-9] ' apps/*/CLAUDE.md   # 포트 표 '행'이 복제됐는지 — 무결과여야 한다
```

> 복제 검사는 **표의 행 형태**(`| 8081 |`)를 찾는다. `grep -c '포트'`처럼 낱말로 세면 3건이 잡히는데
> 전부 "`apps/CLAUDE.md` 포트 표에서 …"처럼 **참조하는 문장**이다. 참조는 복제의 반대이고 권장되는
> 형태이므로, 낱말 단위 검사는 지켜야 할 것을 위반으로 센다.

### 실측 결과 (2026-08-21)

```text
── 문서 보유 ────────────────────────────────────────
> 앱 11종                                     11/11 보유 (이전 3/11)
  auth-service        gateway            job-service          (기존)
  master-service      telemetry-service  realtime-service     (신규)
  config-server       discovery-server                        (신규)
  ems-service         pms-service        autonomous-service   (신규)

── 중복 ────────────────────────────────────────────
> 앱별 문서의 포트 표 복제                      0
> 소유 스키마 표 복제                           0
                                               → 층 분리 유지
```

## 다음 단계

**함정 #1(`master-service`의 dev 프로파일)** 이 이번 회차에서 새로 발견된 유일한 실질 결함이다. `주장-대조표.md`에 명제로 등록했으므로 sync-02가 다시 집어 올린다.

**sync-02**에서 이월 명제 19건과 D6을 함께 처리한다.
