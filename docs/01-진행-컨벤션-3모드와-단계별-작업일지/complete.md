# docs 01 — complete: 진행 컨벤션 3모드와 단계별 작업일지

- 일자: 2026-10-07
- 상태: ✅ 완료 — recycle 1회. 블로커 4건 전부 해소, 비블로커 20건 중 14건 반영·6건 수용
- 커밋: 아직 없음 — 사용자가 한다. `docs/seminar/` 3파일(이 작업 외 변경)을 분리할 것(N4)

## 검증 — plan.md가 착수 전에 정한 6개

```bash
# 1) 구조
ls docs/01-진행-컨벤션-3모드와-단계별-작업일지/

# 2) /step 이식성
grep -c 'swtp' ~/.claude/skills/step/SKILL.md            # 0
grep -c 'project()' ~/.claude/skills/step/SKILL.md       # 0 (불변식 표 잔존)
grep -c 'Flyway' ~/.claude/skills/step/SKILL.md          # 0 (판정표 잔존)
grep -c '사용자에게 묻는다' ~/.claude/skills/step/SKILL.md  # 1

# 3) rules 크기
wc -l .claude/rules/*.md                                  # 합계 ≤ 200

# 5) 기존 문서 무변경
git status --porcelain -- 'apps/*/docs' infrastructure/docs starters/docs docs/scaffold CLAUDE.md 'apps/*/CLAUDE.md'
find apps infrastructure starters -path '*/docs/*.md' | wc -l   # 15

# recycle-1 추가 — B1 복제 해소: 각 문구가 한 파일에만 있어야 한다
for p in '최대값 + 1' '3번째 블로커' '자기 입력만' '경량이어도' '인프라를 임의로' '발단 · 검토 관점' '같은 커밋'; do
  grep -l -- "$p" ~/.claude/skills/{step,work,ask,explain}/SKILL.md .claude/rules/*.md; done
```

4)와 6)은 명령이 아니라 fresh 에이전트 보고다 — 아래 「실측 결과」에 요지를, 전문은 review.md에.

### 실측 결과 (2026-10-07, recycle-1 반영 후)

```text
── 1) 구조 ─────────────────────────────────────────
README.md  plan.md  task.md  review.md  recycle-1.md  complete.md     ← 6개 전부

── 2) /step 이식성 ──────────────────────────────────
swtp: 0 / project(): 0 / Flyway: 0 / 묻는다: 1

── 3) rules 크기 ────────────────────────────────────
   17 .claude/rules/invariants.md
   35 .claude/rules/perspectives.md
  101 .claude/rules/worklog.md
  153 total                              ← 200 이내

── 5) 기존 문서 무변경 ──────────────────────────────
(git status 출력 0줄)                     ← apps/*/docs·infrastructure·starters·scaffold·CLAUDE.md 변경 없음
15                                       ← 기존 단일 파일 문서 수 (plan.md의 "11개"는 오기, N1)

── B1 복제 grep ─────────────────────────────────────
  [최대값 + 1]      → .claude/rules/worklog.md
  [3번째 블로커]     → .claude/rules/worklog.md
  [자기 입력만]      → step/SKILL.md
  [경량이어도]       → step/SKILL.md
  [인프라를 임의로]   → .claude/rules/worklog.md     ← 1차 재grep에서 step에도 있어 9절에서 삭제, 2차에 단일
  [발단 · 검토 관점] → .claude/rules/worklog.md
  [같은 커밋]        → .claude/rules/worklog.md
                                         ← 7개 문구 모두 단일 출처

── B3 강제 수단 ─────────────────────────────────────
ask/SKILL.md:4:     disallowed-tools: Edit, Write, NotebookEdit
ask/SKILL.md:13:    … **`EnterPlanMode` 도구를 호출**한다 …
explain/SKILL.md:4: disallowed-tools: Edit, Write, NotebookEdit
explain/SKILL.md:11: … **`EnterPlanMode` 도구를 호출**하고 …

── B2 /work ─────────────────────────────────────────
work/SKILL.md:16:   1. **Plan mode가 켜져 있으면 멈춘다.** …

── 체크박스·프론트매터(docs/01·rules) ───────────────
0건 / 첫 바이트 '#'(프론트매터 없음)
```

**4) 스킬 동작 — fresh 에이전트 2명 (recycle-1 이전 판 기준)**

| 모의 호출 | 확인한 것 | 결과 |
|---|---|---|
| `/work` "ems-service 제어그룹 목록 조회 페이지네이션" (격리 워크트리) | 읽은 순서 25개 파일: rules 3 → ems docs 4·미착수·scaffold steps·plan.md·architecture → **코드는 19번째부터** / 생성 파일 `apps/ems-service/docs/제어그룹-목록-페이지네이션/{README,plan}.md` **번호 없음** / `git diff --stat` 무변경 / 게이트에서 정지, 승인·기각 임의 결정 안 함 | **통과** |
| `/explain` "gateway 02" | 읽은 순서: `worklog.md` → `apps/gateway/docs/02` 전문 → `git show --stat` 4개 커밋 → 코드 5파일(문서가 지목한 것만) / 「검증」 2)·3) 읽기 전용 명령 재실행해 문서 수치와 일치 확인 / 파일 변경 없음 | **통과** |

워크트리와 브랜치는 검증 후 제거했다(`git worktree remove --force` + `git branch -D`).

**6) 리뷰** — 중량. 전용 리뷰어 fresh 1명, 산출물 + plan.md만 지참. 결과는 review.md. 블로커 4건이 나왔고 전부 사실이었다 — 특히 B3(기각한 대안을 그대로 구현)은 개발한 에이전트가 자기를 채점했으면 잡히지 않았을 종류다.

## 블로커 대조표 — review.md의 번호 전부

| # | 처리 | 어디에 |
|---|---|---|
| **B1** 복제 | **해소** | recycle-1 「단일 출처 표」대로 — task.md 「구현 상세 › 단일 출처」. `/step` 5절 항목 표 → 참조, 6·8절 → `worklog.md` 참조, 9절 인프라 문장 삭제. `/work` 전이표·rules 읽기 삭제. `perspectives.md`·`invariants.md`에서 방법·절차 문장 삭제. 재grep 7문구 단일 출처 |
| **B2** `/work` Plan mode 지시 실행 불가 | **해소** | `/work` 1절 "Plan mode면 멈춘다". task.md 「번호 없음 = 미승인」 정정. 아래 알려진 한계 2 |
| **B3** `/ask`·`/explain` 비강제 | **해소** | 프론트매터 `disallowed-tools` + `EnterPlanMode` 호출 — task.md 「Plan mode 강제의 실제 수단」 |
| **B4** CLAUDE.md 「갱신 규칙」 충돌 미보고 | **해소** | task.md 변경 내역 CLAUDE.md 행에 충돌 기록. 아래 다음 단계 1 |
| N1 11개 → 15개 | 해소 | `worklog.md` 37행, task.md. plan.md는 동결 — 알려진 한계 4 |
| N2 다이어그램 번호 | 해소 | `/step` 0절을 §번호로 |
| N3 비루트 기각 경로 | 해소 | `worklog.md` "항상 루트 `docs/미착수/`" |
| N4 seminar 변경 혼입 | **수용** | 이 작업 외. 커밋 분리 — 알려진 한계 5 |
| N5 `explain` rules 무조건 참조 | 해소 | "있으면 … 없으면 루트 CLAUDE.md 문서 절" |
| N6 `/step` 56행 절 제목 | **수용** | "있으면" 조건부. 알려진 한계 6 |
| N7 코드 0줄의 경량 리뷰 | 해소 | `perspectives.md` "코드 변경이 있을 때 … 0줄이면 대상 없음 기록" |
| N8 README 링크 선행 | 해소 | review.md·complete.md 생성으로 자연 해소 |
| N9 257행 검증 불가 | **수용** | 알려진 한계 7 |
| N10 검증 4 리뷰어 미재현 | **수용** | 모의 호출 2건 보고로 대체 — 위 4). 알려진 한계 3 |
| N11 rules 미커밋 | **수용** | 커밋은 사용자 — 알려진 한계 1 |
| N12 README 제목 번호 | 해소 | `worklog.md` 양식 + 번호 절 |
| N13 영향 범위 vs 문체 | 해소 | `worklog.md` 문체 절 예외 구절 |
| N14 `libs/` 무게중심 | 해소 | `worklog.md` 위치표 |
| N15 최초 선례 행 | 해소 | `perspectives.md` 판정표 + 중량 조건 |
| N16 이중 조회 | 해소 | `/work`에서 rules 읽기 삭제 |
| N17 git 범위 | 해소 | `explain` 1절 3 |
| N18 재실행 경계 | 해소 | `explain` 1절 끝 |
| N19 단일 파일형 인용 | 해소 | `explain` 2절 |
| N20 분량 | 해소 | `explain` 2절 첫 항목 |

빠진 번호 없음 — 24개.

## 알려진 한계

1. **rules 3파일과 docs/01이 커밋되기 전까지는 워크트리·격리 에이전트에서 스킬이 동작하지 않는다**(N11, `/work` 모의 호출이 실측). `/step` 2절대로 "rules가 없다"며 묻고 멈춘다. 커밋이 이 규약의 발효 조건이다.
2. **게이트 이전의 무수정은 하네스가 아니라 절차가 보장한다.** `/work`는 Plan mode 밖에서 돌아야 plan.md를 쓸 수 있으므로, 승인 전에 코드를 건드리지 않는다는 보장은 `/step` 5절의 지시("승인 전에는 `docs/<slug>/`만 쓴다")뿐이다. 결정 2의 Plan mode 강제는 `/ask`·`/explain`에만 걸린다. 원안이 이 비대칭을 보지 못했다 — plan.md 결정 5는 그대로 유효하되 "Plan mode 안에서"가 아니라 "Plan mode 밖, 절차로"가 실제 운용이다.
3. **recycle-1 수정 후 스킬 동작(검증 4)을 재모의하지 않았다.** 수정이 참조 치환·프론트매터 추가·문장 삭제라 동작 경로가 바뀌지 않는다고 판단했다. `➖ 검증불가`가 아니라 **의도적 미재현**이다. `disallowed-tools`가 실제로 Edit를 막는지는 다음 `/ask` 실사용에서 처음 확인된다.
4. **plan.md에 "11개"가 동결됐다.** 실제는 15개. plan.md 동결 원칙(한 글자도 안 고친다)의 첫 비용이다 — 틀린 수가 승인 문서에 남고, 정정은 여기에만 있다.
5. **`docs/seminar/` 3파일 변경이 작업 트리에 섞여 있다**(N4). 이 작업 책임이 아니며 task.md 변경 내역에 "이 작업 외"로 적었다. 커밋 시 `git add -A`를 쓰면 함께 들어가고, seminar README가 가리키는 `-v2.html`은 존재하지 않아 깨진 참조가 커밋된다.
6. `/step` 2절이 "문서 위치·갱신 규칙을 선언한 절이 있으면"으로 조건부이지만 그 표현 자체가 swtp CLAUDE.md의 구성을 전제한 잔재다(N6). 다른 리포에서 틀리게 동작하지는 않는다.
7. `/step` 개정 전 "257행"은 전역 스킬이 git 추적이 아니라 영구히 검증 불가다(N9). 전역 스킬 디렉토리의 버전 관리는 별건.
8. **plan.md 결정 1의 경계 서술과 실제가 다르다.** plan.md는 "`/work`는 모드 진입과 파일 전이를 소유"라 했으나 recycle-1에서 파일 전이의 단일 출처를 `worklog.md`로 옮겼다(B1). `/work`는 진입만 소유한다. 결정의 취지(`/step` 유지, 모드 진입점 분리)는 그대로다.
9. **`/code-review`가 이번 작업에 대상이 없었다.** 코드 0줄. `perspectives.md`를 N7대로 고쳤으나 "문서만 바뀐 작업의 경량 리뷰는 무엇인가"는 비어 있다 — 다음에 문서만 바뀌는 경량 작업이 올 때 정한다.

## 다음 단계

1. **`/claude-md-sync` 실행** — 루트 CLAUDE.md 「갱신 규칙」("같은 파일에서 굴린다", `🔍 논의`)을 새 규약(디렉토리 + 단계 파일, `🔍 계획`, 경로는 `.claude/rules/worklog.md`)에 맞춘다(B4). 이 작업은 CLAUDE.md를 고치지 않았다.
2. **커밋** — `.claude/rules/` 3파일 + `docs/01-…/` 6파일 + `docs/미착수.md`. `docs/seminar/` 3파일은 **별도 커밋**. 전역 스킬 4파일은 리포 밖이라 커밋 대상이 아니다.
3. **메모리 갱신** — `dev-cycle-step-skill.md`가 단일 파일·`NN-<주제>.md` 서술로 남아 있다. 이 작업 완료 직후 갱신한다.
4. **gateway 02 문서 잔재 2건** — `/explain` 모의 호출 실행자가 발견. 「검증」 소제목 "컨테이너 실측 절차 — 미실행"이 실측 완료 뒤에도 남아 있고, `jwks-uri` 로컬 기본값을 grep 패턴에서 제외하는 이유가 적혀 있지 않다. 완료 문서 소급 수정 원칙과 충돌하므로 사용자가 판단한다.
5. **`/step` 4·7절의 소환·리뷰 방법이 `perspectives.md` 없이도 성립하는지** 다른 리포에서 한 번 써 본다 — 이식성의 실측은 아직 없다.
6. **문서만 바뀌는 작업의 경량 리뷰 구성**(알려진 한계 9).
