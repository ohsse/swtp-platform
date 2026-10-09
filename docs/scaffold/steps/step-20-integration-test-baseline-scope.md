# Step 20 — 통합 테스트 기준형의 적용 범위 확정과 잠금

- 일자: 2026-08-21
- 상태: ✅ 완료 (앱 11종 검증 통과. 위반 주입 실험으로 검증이 헛돌지 않음을 확인)
- 관련: [sync-01](../../sync/sync-01-20260821.md) D6, [step-18](step-18-gateway-route-convention.md), [step-19](step-19-per-service-claude-md.md)

## 발단

sync-01의 마지막 미결 항목이다. `apps/CLAUDE.md`가 *"`AbstractIntegrationTest`가 기준형이고 **앱마다** 복제한다"* 고 적었는데 실측은 **11개 중 5개**뿐이었다.

D3·D4(step-18)와 D5(step-19)는 전부 "문서가 옳고 코드가 안 따라온" 경우였다. 그래서 이 항목도 같은 종류로 보고 "나머지 6개에 도입한다"가 기본 선택지였다.

**조사해 보니 반대였다.**

| 조건 | 앱 | `AbstractIntegrationTest` |
|---|---|---|
| `swtp-persistence-starter` 또는 `swtp-kafka-starter` 의존 | master · telemetry · job · auth · realtime | **5/5 보유** |
| 둘 다 미의존 | gateway · config-server · discovery-server · ems · pms · autonomous | **6/6 미보유** |

**예외가 하나도 없다.** 실제 규칙은 처음부터 "DB나 Kafka를 쓰는 앱마다"였고, 문서의 "앱마다"가 **과잉 주장**이었을 뿐이다. gateway에 Testcontainers를 붙이는 것은 띄울 인프라가 없으므로 순수한 낭비다.

띄우는 컨테이너까지 의존과 1:1이었다 — auth-service는 PG만(kafka-starter 미의존), realtime-service는 Kafka만(persistence-starter 미의존)이다.

> **"코드가 안 따라온 것"과 "문서가 과잉 주장한 것"을 가르는 방법**: 위반이라고 지목된 대상을 규약대로 고쳤을 때 **실제로 얻는 것이 있는지** 본다. gateway에 Testcontainers를 붙이면 느려지기만 하고 검증되는 것이 없다 — 그러면 문서가 틀린 것이다. 반대로 step-18의 gateway 구방식 라우트는 고쳤더니 잠재 404까지 사라졌다.

## 결정 (2026-08-21)

### 1. 적용 범위를 의존 기준으로 한정한다

`apps/CLAUDE.md`를 **"`swtp-persistence-starter`나 `swtp-kafka-starter`를 의존하는 앱마다"** 로 고친다. 둘 다 쓰지 않는 앱은 `*ApplicationTest`(컨텍스트 로드)만 둔다.

스캐폴드 3종(ems·pms·autonomous)에는 **"그 스타터를 붙이는 커밋이 기준형을 함께 만드는 커밋"** 이라고 시점을 못박는다. 나중에 붙이는 것이 아니라 같은 커밋이다.

### 2. 띄우는 컨테이너는 의존한 스타터와 일치시킨다

안 쓰는 컨테이너를 띄우면 테스트가 그만큼 느려지기만 한다. 지금 5종이 이미 그렇게 하고 있으므로 관행을 규칙으로 올리는 것이다.

### 3. `swtp.spring-boot-app`이 `check`에서 검증한다

`verifyIntegrationTestBaseline` 태스크를 추가한다. 규약을 어겨도 **앱은 정상 동작하므로 컴파일러도 런타임도 알려주지 않는다** — step-18에서 라우팅 규약을 테스트로 잠근 것과 같은 이유다.

**의도적으로 configuration 시점에 세우지 않았다.** 같은 파일의 앱간 의존 금지 검증은 configuration에서 즉시 던지는데, 그쪽은 *있어서는 안 될 상태*이고 이쪽은 *아직 안 끝난 상태*다. 스타터를 먼저 붙이고 테스트를 나중에 쓰는 중간 상태에서도 `compileJava`·`bootRun`은 돌아야 한다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `build-logic/src/main/groovy/swtp.spring-boot-app.gradle` | `verifyIntegrationTestBaseline` 태스크 추가, `check`가 의존 |
| `apps/CLAUDE.md` | "통합 테스트 표준형" 절 — 적용 범위 한정, 컨테이너 일치 규칙, 스캐폴드 도입 시점 |
| `build-logic/CLAUDE.md` | 플러그인 표에 검증 추가 + **검증 두 개의 세기가 다른 이유** |
| 앱 11종의 테스트 코드 | **변경 없음** — 이미 규약을 지키고 있었다 |

---

## 함정 기록

1. **정합성 점검의 기본 처방("코드를 고친다")이 여기서는 틀렸다.** sync-01은 불일치를 "문서가 낡음"으로 처리해 세탁하지 말라고 규정했는데, 그 규정을 기계적으로 적용하면 이번에는 **반대 방향으로 틀린다** — gateway·config-server·discovery-server에 쓸모없는 Testcontainers가 붙는다. 판별식은 위 인용문이다: **고쳤을 때 실제로 얻는 것이 있는가.**

2. **검증의 세기를 잘못 고르면 개발이 막힌다.** 처음에 앱간 의존 검증과 같은 자리(configuration `afterEvaluate`에서 즉시 throw)에 두려 했다. 그러면 스타터를 붙인 직후부터 `compileJava`조차 실패해, 테스트를 쓰려고 IDE를 여는 것부터 막힌다. 규약 검증을 추가할 때 **"어겨서는 안 되는 상태"인지 "아직 안 끝난 상태"인지**로 붙일 지점을 고른다.

3. **구성 캐시 때문에 태스크 실행 시점에 `project`를 참조할 수 없다.** `fileTree(...).isEmpty()`와 의존 목록을 **configuration 시점에 boolean·문자열로 확정**해 `doLast`에 넘긴다. 실행 시점에 `project.fileTree`를 부르면 구성 캐시가 깨진다.

4. **검증을 추가하면 그것이 헛도는지 반드시 확인한다.** 전 앱이 통과하는 검증은 "규약이 지켜지고 있다"와 "검증이 아무것도 안 본다"를 구분하지 못한다. ems-service에 `swtp-persistence-starter`를 **임시로 붙여 실패를 유도한 뒤 되돌리는** 실험으로 확인했다(아래 검증). step-18의 라우팅 잠금은 이 실험을 하지 않았다 — 논리로만 확인했다.

## 알려진 한계

1. **파일 존재만 검사한다.** `AbstractIntegrationTest.java`가 있기만 하면 통과한다 — 그 안에서 Testcontainers를 쓰는지, compose와 같은 이미지를 쓰는지, H2로 바꿔치기하지 않았는지는 보지 않는다. 이미지 일치는 `docs/sync/주장-대조표.md`의 `A6`이 대신 잰다.

2. **`master-service`는 기준형이 있지만 실행 가능한 테스트가 0개인데도 통과한다**(step-19 함정 #3). 이 검증이 잡는 것은 "기준형이 없는 앱"이지 "테스트가 없는 앱"이 아니다. 후자는 `failOnNoDiscoveredTests` 가드가 잡아야 하는데 master-service가 그것을 꺼 두었다.

3. **컨테이너-스타터 일치(결정 2)는 잠기지 않는다.** 문서 규칙으로만 남았다 — 테스트 소스를 파싱해야 해서 비용 대비 이득이 낮다고 봤다.

## 검증

```bash
./gradlew verifyIntegrationTestBaseline --console=plain        # 전 앱 통과해야 한다
# 위반 주입 실험 — ems-service에 persistence-starter를 임시로 추가한 뒤
./gradlew :apps:ems-service:compileJava                        # 통과해야 한다 (중간 상태 허용)
./gradlew :apps:ems-service:check                              # 실패해야 한다
```

### 실측 결과 (2026-08-21)

```text
── 정상 상태 ───────────────────────────────────────
> verifyIntegrationTestBaseline                11개 앱 전부 실행
                                               BUILD SUCCESSFUL (5s)

── 위반 주입 (ems-service + persistence-starter) ───
> :apps:ems-service:compileJava                BUILD SUCCESSFUL   ← 중간 상태 허용
> :apps:ems-service:check                      BUILD FAILED       ← 규약 위반 차단
  메시지: ":apps:ems-service는 [:starters:swtp-persistence-starter]를 의존하므로
           통합 테스트 기준형이 필요하다. src/test/java/**/AbstractIntegrationTest.java를
           만들 것 — apps/master-service의 것이 기준형이다(apps/CLAUDE.md).
           H2로 대체하지 않는다(루트 불변식 5)."
> build.gradle 복구                            git diff 무변경

                                               → 검증이 헛돌지 않는다
```

### 운영 확인 지점

`ems`·`pms`·`autonomous`에 DB를 붙이는 커밋에서 이 검증이 처음 발동한다. 그때 **H2로 우회하려는 유혹**이 생기는데(Testcontainers는 Docker Desktop을 요구한다) 루트 불변식 5가 금지한다 — 에러 메시지가 그 사실을 함께 알린다.

## 다음 단계

**sync-01의 미결 항목이 전부 소진됐다.** D3·D4는 step-18, D5는 step-19, D6은 이 문서다.

남은 것은 sync-01 이후 새로 쌓인 것들이다 — 대조표의 이월 명제 19건과 step-19에서 등록한 `A13`(master-service의 `dev` 프로파일)·`A14`(ems·pms 스키마-의존 불일치)·`A15`(테스트 가드 해제). **sync-02가 여기서 시작한다.**
