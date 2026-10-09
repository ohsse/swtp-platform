package com.mindone.editor.inp.network.parser;

import java.util.List;

/**
 * {@code [RULES]} 섹션의 규칙 한 블록.
 *
 * <p>규칙은 {@code RULE <id>} 로 시작해 다음 {@code RULE} 또는 섹션 종료까지의 여러 라인(IF/AND/THEN/
 * PRIORITY 등)으로 구성된다. 한 블록을 ID 와 절(clause) 행 목록으로 보관한다.</p>
 *
 * @param id      규칙 ID({@code RULE} 다음 토큰)
 * @param clauses 규칙 본문 절 행 목록({@code RULE} 헤더 행은 제외)
 */
public record RuleBlock(String id, List<SectionRow> clauses) {
}
