// 세미나 덱이 참조하는 리포지토리 파일들을 HTML 안에 스냅샷으로 심는다.
//
// 덱은 소스 참조를 클릭하면 fetch() 로 실제 파일을 읽어 슬라이드에 펼친다. 그런데
// 브라우저는 file:// 로 열린 페이지를 opaque origin 으로 취급해 옆 파일 읽기를 막는다
// (동일 출처 정책). 그래서 더블클릭으로 열면 코드가 보이지 않았다.
//
// 이 스크립트가 파일 내용을 HTML 안에 미리 심어 두면 런타임 fetch 가 필요 없어진다 —
// 파일 하나만 있으면 서버도 IDE도 없이 어디서든 코드까지 다 보인다.
//
// 실행: node docs/seminar/embed-sources.mjs [대상파일명]   (어느 디렉토리에서 실행해도 된다)
//       대상을 주지 않으면 최초판을 갱신한다. 덱이 여러 벌이면 각각 한 번씩 돌린다.

import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));      // docs/seminar
const REPO = resolve(HERE, '..', '..');                    // 리포지토리 루트
const HTML = join(HERE, process.argv[2] || 'swtp-platform-아키텍처-세미나.html');

const BEGIN = '<!-- SRCDUMP:BEGIN — node docs/seminar/embed-sources.mjs 가 생성한다. 손으로 고치지 않는다 -->';
const END = '<!-- SRCDUMP:END -->';

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const kb = (n) => (n / 1024).toFixed(1) + 'KB';

let html = readFileSync(HTML, 'utf8');
const before = Buffer.byteLength(html, 'utf8');
const EOL = html.includes('\r\n') ? '\r\n' : '\n';   // 원본 줄바꿈을 그대로 따라간다

// ── 1. 대상 목록은 SRC 맵에서 뽑는다 ──────────────────────────────────
// 참조 목록의 진실 원천은 HTML 안의 SRC 맵 하나다. 여기에 목록을 두 번 쓰지 않는다.
const srcBlock = html.match(/var SRC = \{([\s\S]*?)\n\s*\};/);
if (!srcBlock) {
  console.error('SRC 맵을 찾지 못했다 — HTML 구조가 바뀌었는지 확인할 것.');
  process.exit(1);
}
const paths = [...new Set(
  [...srcBlock[1].matchAll(/\[\s*'([^']+)'\s*,\s*\d+\s*\]/g)].map((m) => m[1])
)];

// ── 2. 읽는다. 없는 파일은 경고만 남기고 건너뛴다 ─────────────────────
const files = {};
let raw = 0;
let missing = 0;
for (const p of paths) {
  const abs = join(REPO, p);
  try {
    statSync(abs);
  } catch {
    console.warn('  ! 없음 — ' + p);
    missing++;
    continue;
  }
  // BOM 제거 + 줄바꿈 정규화 — 뷰어가 어차피 \n 기준이고, 매번 같은 결과가 나와야 한다.
  const text = readFileSync(abs, 'utf8').replace(/^﻿/, '').replace(/\r\n/g, '\n');
  files[p] = text;
  raw += Buffer.byteLength(text, 'utf8');
}

// ── 3. 직렬화 ─────────────────────────────────────────────────────────
// < > & 를 유니코드 이스케이프로 바꾼다. JSON 문자열 안에서 유효하고 파싱하면 원래
// 문자로 돌아오지만, </script> 나 주석 여는 시퀀스가 원천적으로 생기지 않는다 —
// 즉 어떤 파일 내용도 이 HTML 을 깨뜨릴 수 없다.
const d = new Date();
const pad = (n) => String(n).padStart(2, '0');
const payload = {
  generated: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
  files,
};
const json = JSON.stringify(payload).replace(
  /[<>&]/g,
  (c) => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0')
);

const dump = BEGIN + EOL +
  '<script type="application/json" id="srcdump">' + json + '</script>' + EOL +
  END;

// ── 4. 주입 ───────────────────────────────────────────────────────────
// 마커가 있으면 그 사이만 갈아끼운다. 전체를 재직렬화하지 않으므로 나머지 서식이 보존된다.
const re = new RegExp(escapeRe(BEGIN) + '[\\s\\S]*?' + escapeRe(END));
if (re.test(html)) {
  html = html.replace(re, () => dump);          // 함수로 넘겨 $& 같은 치환 시퀀스 해석을 막는다
} else {
  // 스냅샷은 이를 읽는 인라인 스크립트보다 먼저 DOM 에 있어야 한다.
  const anchor = html.match(/\r?\n<script>\r?\n\(function \(\) \{/);
  if (!anchor) {
    console.error('인라인 <script> 앵커를 찾지 못했다 — 주입 위치를 확인할 것.');
    process.exit(1);
  }
  html = html.slice(0, anchor.index) + EOL + dump + html.slice(anchor.index);
}

writeFileSync(HTML, html, 'utf8');

const after = Buffer.byteLength(html, 'utf8');
console.log(
  `임베드 ${Object.keys(files).length}개 파일 · ${kb(raw)} → HTML ${kb(after)}` +
  ` (이전 ${kb(before)})` + (missing ? ` · 누락 ${missing}개` : '')
);
