# docs 01 — 진행 컨벤션: 3모드와 단계별 작업일지

- 주제: 대화 모드 3종(`/ask`·`/explain`·`/work`)과 slug 디렉토리 + 단계별 파일 작업일지를 프로젝트 진행 규약으로 정형화한다
- 작업일자: 2026-10-07 착수 → 2026-10-07 완료
- 상태: ✅ 완료 — recycle 1회
- 토의내용: `/step`을 유지하고 모드 진입점 3개를 앞에 두며, 단일 파일 템플릿을 단계별 파일로 바꾼다. 11개 결정, 갈린 의견 없음(사용자와 1:1 토의, 전문가 소환 0명) → [plan.md](plan.md)
- 작업내용: 리포 `.claude/rules/` 3파일 신규(작업일지 구조의 단일 출처), 전역 스킬 3개 신규 + `/step` 개정, `docs/미착수.md` 보강, 이 디렉토리(첫 적용 사례) → [task.md](task.md)
- 결과: 중량 리뷰(기존 결정 뒤집음). 블로커 4건 — 복제, Plan mode 강제 미구현 2건, CLAUDE.md 충돌 미보고 — 전부 해소. 비블로커 20건 중 14 반영·6 수용. 검증 6개 중 5개 실측 통과, 1개(스킬 동작)는 fresh 에이전트 2명 보고로 → [review.md](review.md) · [recycle-1.md](recycle-1.md) · [complete.md](complete.md)
- 관련: `.claude/rules/worklog.md` · `~/.claude/skills/step/SKILL.md` · [docs/미착수.md](../미착수.md) · 선례 [gateway 02](../../apps/gateway/docs/02-경로-규약-확정과-인증-기본값-정렬.md)(단일 파일 관례의 마지막 완성형) · **다음**: `/claude-md-sync`(루트 CLAUDE.md 「갱신 규칙」 충돌)
