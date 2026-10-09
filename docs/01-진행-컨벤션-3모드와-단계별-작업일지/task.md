# docs 01 — task: 진행 컨벤션 3모드와 단계별 작업일지

- 일자: 2026-10-07
- 상태: 🔧 진행 → 🧐 리뷰 → ♻ 재작업(1회) → 🧐 리뷰

## 변경 내역

| 대상 | 변경 |
|---|---|
| `.claude/rules/invariants.md` | 신규. `/step` 3절의 불변식 7개 표를 옮김 + "자주 걸리는 요청" 열. recycle-1에서 절차 문장(뒤집는 요청 처리·CLAUDE.md 안 고침·3명 소환)을 **제거**하고 `/step`·`perspectives.md` 참조로 — 표와 swtp 실패 예만 남김 |
| `.claude/rules/worklog.md` | 신규. `/step` 2·6·8·9절(위치·조회 순서·템플릿·기각) + 이번 결정(디렉토리 구조·번호·README 양식·단계 파일의 절·상태 머신·recycle·세탁 방지·문체). recycle-1에서 **파일 전이의 단일 출처**로 선언하고 전이표를 가짐. 15개 문서 수 정정, `libs/docs/` 추가, 기각은 항상 루트 `미착수/`, README 제목 번호는 승인 시 삽입, 문체 예외 구절 |
| `.claude/rules/perspectives.md` | 신규. `/step` 4절 판정표·역할별 입력 + 7절 발동 조건을 "리뷰 깊이 판정표"로. recycle-1에서 소환 **방법** 문장 제거(`/step`으로 일원화), "리포 최초 선례" 행 추가, 경량 `/code-review`를 "코드 변경이 있을 때"로 |
| `~/.claude/skills/step/SKILL.md` | 개정. ① rules 3파일을 읽는 2절 신설, swtp 데이터 전부 제거 ② 5절 게이트 산출물을 plan.md 초안으로 ③ 6절 단일 파일 원칙을 plan.md 동결로 ④ 7절 리뷰를 필수 2단계로 ⑤ 8절 recycle 신설 ⑥ 템플릿 절 삭제. recycle-1에서 5절 plan.md 항목 표·6절 번호/git mv/대조 문장을 `worklog.md` 참조로 치환, 0절 다이어그램 번호를 절 번호와 정렬 |
| `~/.claude/skills/ask/SKILL.md` | 신규. recycle-1에서 **강제 수단 교체** — 프론트매터 `disallowed-tools: Edit, Write, NotebookEdit` + Plan mode 표시 없으면 `EnterPlanMode` 도구 호출. 안내 문장 방식 폐기 |
| `~/.claude/skills/explain/SKILL.md` | 신규. 읽기 순서(작업일지 → git → 코드), 출처 지목, 기록 없음 분리. recycle-1에서 `ask`와 같은 강제 + rules 조건부 참조 + git 범위(본 작업 커밋 + 문서가 언급한 커밋) + 「검증」 명령 재실행 경계(읽기 전용만) + 단일 파일형 인용 예시 + 분량 지침 |
| `~/.claude/skills/work/SKILL.md` | 신규. recycle-1에서 **파일 전이표와 rules 읽기를 삭제** — Plan mode면 정지하라는 진입 확인 + `/step` 호출만 남은 얇은 스킬 |
| `docs/미착수.md` | 머리말에 `미착수/<slug>/plan.md` 보존 규칙 추가, 표에 `plan.md` 열 추가(기존 1행은 `—`) |
| `docs/01-진행-컨벤션-…/` | 신규. 이 디렉토리 — 첫 적용 사례. recycle-1.md 포함 |
| 루트 `CLAUDE.md` | **변경 없음** — 불변식이 바뀌지 않았다. 단 **「갱신 규칙」 절("문서를 열고(`🔍 논의`) 완료까지 같은 파일에서 굴린다")이 새 규약(디렉토리 + 단계 파일, `🔍 계획`)과 충돌한다.** 이 작업은 CLAUDE.md를 고치지 않으므로 `/claude-md-sync` 대상으로 complete.md 「다음 단계」에 올린다(리뷰 B3) |
| `apps/*/docs/`·`infrastructure/docs/`·`starters/docs/` 기존 **15개** | **변경 없음** — 소급 수정하지 않는다. 초판은 "11개"로 셌다(리뷰 N1) |
| `docs/scaffold/` | **변경 없음** — 동결 |
| `docs/seminar/` 3파일 | **이 작업 외 변경** — 세션 시작 전부터 `M` 상태. 완료 커밋에서 분리해야 한다(리뷰 N4) |
| 메모리 `dev-cycle-step-skill.md` | 완료 후 갱신 예정 — "단일 파일" 서술이 틀려진다 |

## 구현 상세

### 단일 출처 — 누가 무엇을 소유하는가 (recycle-1 이후)

```
worklog.md      = 작업일지 구조 · 단계 파일의 절 · README 양식 · 번호 · 상태 머신(파일 전이) · 기각 · 세탁 방지 · 문체
invariants.md   = 불변식 표
perspectives.md = 조건 → 관점 · 조건 → 리뷰 깊이
/step           = 절차 — 조회 순서의 원칙 · 소환 방법 · 게이트 · 각 파일에 담을 것 · 리뷰 3축 · recycle 원칙 · 제한
/work           = 진입 — Plan mode면 정지 · 범위 하나 · /step 호출
/ask /explain   = 읽기 전용 모드 — EnterPlanMode + disallowed-tools · 읽기 순서 · 답변 형식
```

초판은 `/work`가 "파일 전이표"를 가졌고 그것이 `worklog.md` 상태 머신·`/step` 6절과 **세 벌**이었다(리뷰 B1). 전이의 단일 출처를 `worklog.md`로 정하고 `/work`를 진입점만 남겼다. 이로써 **plan.md 결정 1의 경계 서술("`/work`는 모드 진입과 파일 전이를 소유")과 실제가 다르다** — plan.md는 동결이라 complete.md 「알려진 한계」에 적는다.

### Plan mode 강제의 실제 수단 (recycle-1 이후)

초판은 `/ask`·`/explain`에 "Plan mode를 켜 달라고 안내한다"고 적었다. 그것은 plan.md 결정 2가 **기각한 대안**("규칙 문장만")이다(리뷰 B2). 공식 문서로 확인된 수단 둘을 쓴다.

| 겹 | 수단 | 효과 |
|---|---|---|
| 1 | `EnterPlanMode` 도구 호출 | Plan mode 표시가 없으면 모델이 직접 켠다. 사용자 손을 기다리지 않는다 |
| 2 | 프론트매터 `disallowed-tools: Edit, Write, NotebookEdit` | Plan mode가 꺼져 있어도 이 스킬 안에서는 파일을 쓸 수 없다 |

`/work`는 반대다 — Plan mode가 켜져 있으면 게이트 산출물 `docs/<slug>/plan.md`를 쓸 수 없으므로 **끄고 다시 부르라고 안내하고 멈춘다.** 게이트 이전 무수정은 Plan mode가 아니라 `/step` 절차(승인 전에는 `docs/<slug>/`만 쓴다)가 보장한다.

### rules 로드 방식

`paths:` 프론트매터 없이 셋 다 세션 시작 시 로드된다(CLAUDE.md와 같은 우선순위). `perspectives.md`는 게이트 이전, 즉 파일을 건드리기 전에 필요해 경로 조건이 성립하지 않는다. 컨텍스트 부담이 실측되면 `worklog.md`에만 `paths: ["**/docs/**"]`를 거는 것을 검토한다.

### 번호 없음 = 미승인 — 이 디렉토리가 그 단계를 건너뛴 이유

`docs/<slug>/`(번호 없음) → 승인 시 `git mv` → `docs/NN-<slug>/`. 이 디렉토리는 **Plan mode 안에서** 계획이 `~/.claude/plans/`의 파일로 승인됐으므로 docs/ 밖에서 승인이 끝났고, 번호 없는 단계 없이 `01`로 바로 생겼다. 리뷰 B2가 지적했듯 이것은 "첫 사례의 예외"가 아니라 **Plan mode로 들어오면 늘 그렇게 되는 구조**다. 다음부터 `/work`로 들어오면(Plan mode면 정지) 전이표대로 간다.

## 함정 기록

1. **`.claude/`가 gitignore에 걸려 있는지 먼저 봤다.** 글로벌 ignore(`~/.config/git/ignore`)가 `**/.claude/settings.local.json`만 막고 `.claude/rules/`는 추적 대상이다. 확인 안 하고 썼으면 rules가 로컬에만 있고 팀에 전파되지 않는 상태를 "규약 정형화 완료"로 적을 뻔했다. 같은 이유로 **커밋 전까지는 워크트리·격리 에이전트에서 스킬이 동작하지 않는다**(`/work` 모의 호출이 실측, 리뷰 N9).
2. **`/code-review`는 코드 diff를 보는 도구인데 이번 변경은 코드 0줄이다.** 첫 적용에서 바로 대상이 없었다. `perspectives.md`를 "코드 변경이 있을 때"로 좁혔다(리뷰 N7) — 규약을 만든 작업이 규약을 처음 따르면서 양식의 구멍을 찾는 것이 이 디렉토리를 둔 이유다.
3. **Git Bash 명령줄에 한글 경로를 넣으면 깨진다.** heredoc **본문**의 한글은 UTF-8로 온전히 써지지만, 명령줄 인자의 한글 경로는 코드페이지 변환에서 깨져 `mkdir`·`ls`가 엉뚱한 바이트열을 받았다. 한글 경로의 파일은 Write/Edit 도구로 쓴다. `docs/미착수.md`의 `LF will be replaced by CRLF` 경고는 autocrlf 때문이고 저장소 blob은 원래 LF였다(리뷰어 실측).

### 리뷰에서 나와 고친 것

4. **"복제하지 않는다"를 선언한 산출물이 복제를 가장 많이 했다.** `/step`·`/work`·rules 세 곳에 번호 부여·3회째 처리·plan.md 항목이 각각 있었다. 쓰는 동안은 "참조하기 불편하니 여기도 적어 두자"가 매번 합리적으로 보였다. 단일 출처 표를 **먼저** 그리고 썼어야 했다 — 초판은 쓰고 나서 경계를 설명했다.
5. **"강제한다"고 결정해 놓고 안내 문장을 썼다.** 강제 수단(`EnterPlanMode`·`disallowed-tools`)이 있는지 확인하지 않고 "Plan mode에 위임"을 "사용자가 켜 주겠지"로 읽었다. 결정 2의 기각 사유("규칙 문장만 — 강제가 아님")를 plan.md에 적어 놓고 그대로 만든 것이라, 기각한 대안을 적는 것만으로는 그 길로 가는 것을 못 막는다는 사례다. fresh 리뷰어가 공식 문서를 뒤져 수단을 찾았다.
6. **"어긋나면 보고한다"를 `/step`에 적어 놓고 보고하지 않았다.** 루트 CLAUDE.md 「갱신 규칙」이 단일 파일 관례를 선언하는 것을 알면서(이 작업이 바로 그것을 뒤집는 것이므로) task.md에 "변경 없음"만 적었다. 두 fresh 에이전트(리뷰어, `/work` 실행자)가 독립적으로 잡았다.
7. **"11개"는 15개였다.** 세션 초반 `Glob('apps/*/docs/*.md')`이 11건을 돌려줬고 그 수를 `infrastructure/docs/`·`starters/docs/`를 세지 않은 채 "기존 문서 전부"로 썼다. plan.md에 동결됐다.
