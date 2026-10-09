package com.mindone.editor.inp.network.combiner;

import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.parser.RuleBlock;
import com.mindone.editor.inp.network.parser.SectionRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ParsedInp} 의 비가시 섹션(6.5) 중 <b>설정(OPTIONS/TIMES/REACTIONS/ENERGY) 을 제외한</b> 섹션을
 * 응답용 키-밸류 데이터로 변환한다.
 *
 * <p>가시 객체(절점/관로 등)는 {@link GeoJsonCombiner} 가 GeoJSON 레이어로, 좌표/정점 등 지도 레이아웃은
 * 이미 레이어/메타로 표현하므로 제외한다. EPANET Options 브라우저(이미지)에 대응하는 설정 섹션
 * (OPTIONS/TIMES/REACTIONS/ENERGY)은 {@link OptionsCombiner} 가 별도 {@code options} 로 묶으므로 제외한다.
 * 객체별 속성 섹션(TAGS/DEMANDS/EMITTERS/QUALITY/SOURCES/MIXING/STATUS)은 {@link NetworkAssembler} 가 노드/링크
 * {@code properties} 로 병합하므로 여기서 중복 노출하지 않는다. 나머지 비가시 섹션은 토큰/원본이 아니라 EPANET
 * 의미 필드로 매핑한다. 섹션별 형태:</p>
 * <ul>
 *     <li>REPORT: {@code 파라미터 → 값} 단일 맵</li>
 *     <li>CURVES: 곡선 ID별 {@code {id, curveType, description, xyData:[{x,y}]}} — 타입은 사용처(펌프 HEAD/효율/탱크 부피/GPV)
 *         기반 추론을 우선하고 없으면 직전 주석에서, 설명은 직전 주석에서</li>
 *     <li>PATTERNS: {@code {id, description, multipliers:[]}}(설명은 직전 주석에서)</li>
 *     <li>CONTROLS: {@code {simple, rule}} — EPANET Controls Editor 한 창에 대응하는 제어 묶음.
 *         {@code simple} 은 단순 제어문 원본 텍스트 라인 목록, {@code rule} 은 규칙 블록 {@code {id, content}} 목록
 *         (content 는 본문을 한 문자열로, 텍스트 편집용). 원본에 한쪽만 있어도 두 키 모두 노출된다(없으면 빈 목록)</li>
 *     <li>LABELS: {@code {text, x, y, anchorNode}}(Label Editor 대응) — 지도 표시는 {@code layers.labelLayer}</li>
 *     <li>TITLE: 자유 텍스트 라인 목록</li>
 * </ul>
 *
 * <p>섹션 등장 순서는 {@link InpSectionType} 선언 순서를 따른다. 값 타입은 표시용 문자열을 기본으로 하되,
 * 차트에 쓰이는 CURVES 점/PATTERNS 배율만 숫자(Double)로 둔다.</p>
 */
@Component
public class NonVisualSectionCombiner {

    /** 이미 layers/meta 로 표현되어 제외하는 지도/레이아웃 섹션. */
    private static final Set<InpSectionType> MAP_SECTIONS = EnumSet.of(
            InpSectionType.COORDINATES,
            InpSectionType.VERTICES,
            InpSectionType.BACKDROP,
            InpSectionType.END
    );

    /**
     * {@link OptionsCombiner} 가 EPANET Options 브라우저 구조로 묶는 설정 섹션 — 여기서는 제외하고,
     * 결과는 {@code NetworkService} 가 {@code sections.OPTIONS} 키로 합류시킨다.
     */
    public static final Set<InpSectionType> OPTION_SECTIONS = EnumSet.of(
            InpSectionType.OPTIONS,
            InpSectionType.TIMES,
            InpSectionType.REACTIONS,
            InpSectionType.ENERGY
    );

    /**
     * 노드/링크 {@code properties} 로 병합되어({@link NetworkAssembler}) {@code sections} 에서 중복 노출하지 않는
     * 객체별 속성 섹션. EPANET 속성편집기(6.4)가 객체에 모아 보여주는 값들이라 객체가 단일 출처가 된다.
     */
    private static final Set<InpSectionType> MERGED_INTO_OBJECT_SECTIONS = EnumSet.of(
            InpSectionType.TAGS,
            InpSectionType.DEMANDS,
            InpSectionType.EMITTERS,
            InpSectionType.QUALITY,
            InpSectionType.SOURCES,
            InpSectionType.MIXING,
            InpSectionType.STATUS
    );

    /**
     * 비가시 섹션 데이터를 섹션명 → 데이터 맵으로 만든다(설정 섹션 제외).
     *
     * @param parsed 무손실 파싱 결과
     * @return 섹션명 → 섹션 데이터(등장 순서 유지)
     */
    public Map<String, Object> combine(ParsedInp parsed) {
        Map<String, Object> sections = new LinkedHashMap<>();

        for (Map.Entry<InpSectionType, List<SectionRow>> e : parsed.sections().entrySet()) {
            InpSectionType type = e.getKey();
            if (type.isVisual() || MAP_SECTIONS.contains(type) || OPTION_SECTIONS.contains(type)
                    || MERGED_INTO_OBJECT_SECTIONS.contains(type)) {
                continue;
            }
            // CONTROLS/RULES 는 EPANET Controls Editor 처럼 하나의 CONTROLS 키로 묶는다(simple/rule).
            if (type == InpSectionType.CONTROLS) {
                controlsSection(sections).put("simple", controls(e.getValue()));
            } else if (type == InpSectionType.RULES) {
                controlsSection(sections).put("rule", rules(parsed.ruleBlocks()));
            } else {
                sections.put(type.name(), toSection(type, e.getValue(), parsed));
            }
        }

        // 미지 섹션: 스키마를 알 수 없어 원본 라인 텍스트로 보존(무손실 폴백)
        for (Map.Entry<String, List<SectionRow>> e : parsed.unknownSections().entrySet()) {
            sections.put("UNKNOWN:" + e.getKey(), rawLines(e.getValue()));
        }

        // 원본에 CONTROLS/RULES 가 모두 없어도 제어 편집 UI 가 바인딩할 수 있도록 빈 묶음을 노출한다.
        controlsSection(sections);

        return sections;
    }

    /** 섹션 종류에 맞는 키-밸류 데이터로 변환한다. */
    private Object toSection(InpSectionType type, List<SectionRow> rows, ParsedInp parsed) {
        return switch (type) {
            case TITLE -> titleLines(rows);
            case PATTERNS -> patterns(rows, parsed);
            case CURVES -> curves(rows, parsed);
            case REPORT -> keyValueMap(rows);
            case LABELS -> labels(rows);
            default -> rawLines(rows); // 객체로 병합되는 속성 섹션은 제외되어 도달하지 않음(안전 폴백)
        };
    }

    // ===== 표 섹션 =====

    /**
     * REPORT 등: 첫 토큰을 키, 나머지를 값으로 한 단일 맵.
     *
     * <p>순수 enum 파라미터({@code STATUS}=YES/NO/FULL, {@code SUMMARY}=YES/NO)의 값만 대문자로 통일한다.
     * {@code NODES}/{@code LINKS}(NONE/ALL/ID 목록)나 수리·수질 파라미터(BELOW/ABOVE + 숫자)는 ID·수치가
     * 섞인 부분 콤보라 손대지 않는다.</p>
     */
    private Map<String, String> keyValueMap(List<SectionRow> rows) {
        Map<String, String> map = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            if (row.size() == 0) {
                continue;
            }
            String key = row.token(0);
            String value = joinFrom(row, 1);
            if (key != null && (key.equalsIgnoreCase("STATUS") || key.equalsIgnoreCase("SUMMARY"))) {
                value = EnumNormalizer.upper(value);
            }
            map.put(key, value);
        }
        return map;
    }

    // ===== CURVES / PATTERNS (ID 별 묶음) =====

    /**
     * 곡선 ID별로 점{x,y} 목록을 묶고, 곡선 타입/설명을 채운다. x/y 는 숫자.
     *
     * <p>타입은 <b>사용처 기반 추론</b>(펌프 HEAD→PUMP, 펌프 효율→EFFICIENCY, 탱크 부피곡선→VOLUME,
     * GPV 밸브→HEADLOSS)을 우선한다. 사용처가 없으면 직전 주석(예: {@code ;PUMP: ...})의 첫 토큰으로 보조한다.
     * 설명은 직전 주석에서 추출한다(주석 의존도를 낮춰, 주석이 없거나 형식이 달라도 타입을 잃지 않는다).</p>
     */
    private List<Map<String, Object>> curves(List<SectionRow> rows, ParsedInp parsed) {
        Map<String, String> usageTypes = inferCurveTypes(parsed);
        Map<String, List<Map<String, Object>>> pointsById = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("x", number(row.token(1)));
            point.put("y", number(row.token(2)));
            pointsById.computeIfAbsent(id, k -> new ArrayList<>()).add(point);
        }
        List<Map<String, Object>> out = new ArrayList<>(pointsById.size());
        for (Map.Entry<String, List<Map<String, Object>>> e : pointsById.entrySet()) {
            String comment = parsed.curveComment(e.getKey());
            String type = usageTypes.get(e.getKey());       // 사용처 기반(권위) 우선
            if (type == null) {
                type = curveTypeOf(comment);                // 없으면 주석에서 보조
            }
            // 6.5 Curve Editor(Table 6.8) 라벨: Curve ID / Description / Curve Type / X-Y Data
            Map<String, Object> curve = new LinkedHashMap<>();
            curve.put("id", e.getKey());                    // Curve ID
            curve.put("curveType", EnumNormalizer.upper(type)); // Curve Type enum(PUMP/EFFICIENCY/VOLUME/HEADLOSS) → 대문자 통일
            curve.put("description", descriptionOf(comment)); // Description
            curve.put("xyData", e.getValue());              // X-Y Data([{x,y}])
            out.add(curve);
        }
        return out;
    }

    /**
     * 곡선 ID → 곡선 타입을 사용처에서 추론한다(EPANET 은 곡선 자체에 타입을 저장하지 않고 참조 위치로 결정).
     *
     * <ul>
     *     <li>{@code [PUMPS] ... HEAD <curve>} → PUMP</li>
     *     <li>{@code [ENERGY] PUMP <id> EFFIC <curve>} → EFFICIENCY</li>
     *     <li>{@code [TANKS]} 7번째 토큰(부피곡선) → VOLUME</li>
     *     <li>{@code [VALVES] ... GPV <curve>} → HEADLOSS</li>
     * </ul>
     */
    private Map<String, String> inferCurveTypes(ParsedInp parsed) {
        Map<String, String> types = new LinkedHashMap<>();
        for (SectionRow row : parsed.rows(InpSectionType.PUMPS)) {
            for (int i = 3; i + 1 < row.size(); i += 2) {
                if ("HEAD".equalsIgnoreCase(row.token(i))) {
                    putCurveType(types, row.token(i + 1), "PUMP");
                }
            }
        }
        for (SectionRow row : parsed.rows(InpSectionType.ENERGY)) {
            if ("PUMP".equalsIgnoreCase(row.token(0)) && "EFFIC".equalsIgnoreCase(row.token(2))) {
                putCurveType(types, row.token(3), "EFFICIENCY");
            }
        }
        for (SectionRow row : parsed.rows(InpSectionType.TANKS)) {
            putCurveType(types, row.token(7), "VOLUME"); // ID Elev Init Min Max Diam MinVol VolCurve
        }
        for (SectionRow row : parsed.rows(InpSectionType.VALVES)) {
            if ("GPV".equalsIgnoreCase(row.token(4))) {
                putCurveType(types, row.token(5), "HEADLOSS");
            }
        }
        return types;
    }

    /** 곡선 ID 가 유효하면 타입을 최초 1회 등록한다. */
    private void putCurveType(Map<String, String> types, String curveId, String type) {
        if (curveId != null && !curveId.isBlank()) {
            types.putIfAbsent(curveId, type);
        }
    }

    /** 곡선 주석 {@code <타입>: <설명>} 에서 타입(첫 콜론 이전)을 뽑는다. */
    private String curveTypeOf(String comment) {
        if (comment == null) {
            return null;
        }
        int colon = comment.indexOf(':');
        return colon >= 0 ? comment.substring(0, colon).trim() : null;
    }

    /** 곡선 주석에서 설명(첫 콜론 이후)을 뽑는다. 콜론이 없으면 주석 전체. */
    private String descriptionOf(String comment) {
        if (comment == null) {
            return null;
        }
        int colon = comment.indexOf(':');
        return colon >= 0 ? comment.substring(colon + 1).trim() : comment.trim();
    }

    /** 패턴 ID별로 배율 목록을 묶고, 직전 주석에서 설명을 채운다(여러 행에 걸쳐 이어짐). 배율은 숫자. */
    private List<Map<String, Object>> patterns(List<SectionRow> rows, ParsedInp parsed) {
        Map<String, List<Object>> byId = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            List<Object> multipliers = byId.computeIfAbsent(id, k -> new ArrayList<>());
            for (int i = 1; i < row.size(); i++) {
                multipliers.add(number(row.token(i)));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(byId.size());
        for (Map.Entry<String, List<Object>> e : byId.entrySet()) {
            Map<String, Object> pattern = new LinkedHashMap<>();
            pattern.put("id", e.getKey());
            pattern.put("description", parsed.patternComment(e.getKey())); // 직전 주석(없으면 null)
            pattern.put("multipliers", e.getValue());
            out.add(pattern);
        }
        return out;
    }

    // ===== 문장형 섹션 =====

    /**
     * {@code sections} 의 CONTROLS 묶음 맵을 얻는다(없으면 {@code simple}/{@code rule} 을 빈 목록으로 초기화해 생성).
     *
     * <p>단순 제어(CONTROLS)와 규칙 기반 제어(RULES)는 EPANET 상 같은 Controls Editor 로 다루는 한 쌍이므로
     * 응답에서도 {@code CONTROLS: {simple, rule}} 한 키로 묶는다. 원본에 두 섹션 중 하나만 있거나 둘 다 없어도
     * {@code CONTROLS} 키와 두 하위 키가 항상 빈 목록으로 노출되어(전체 스키마 고정), 프론트가 키 유무 검사
     * 없이 {@code sections.CONTROLS.simple} 로 바인딩할 수 있다.</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> controlsSection(Map<String, Object> sections) {
        return (Map<String, Object>) sections.computeIfAbsent(InpSectionType.CONTROLS.name(), k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("simple", new ArrayList<>());
            m.put("rule", new ArrayList<>());
            return m;
        });
    }

    /**
     * CONTROLS.simple: 단순 제어문을 <b>원본 텍스트 라인 목록</b>으로 보존한다.
     *
     * <p>EPANET 의 Controls Editor(매뉴얼 6.5, Fig 6.3)는 단순/규칙 제어를 모두 다루는 <b>텍스트 편집기 창</b>이다.
     * 따라서 의미 필드로 분해하지 않고 RULES 와 동일하게 원본 제어문을 그대로 노출한다(프론트는 {@code join('\n')}
     * 으로 한 텍스트 영역에 바인딩). 주석 전용 줄은 파서 단계에서 이미 제외된다.</p>
     */
    private List<String> controls(List<SectionRow> rows) {
        List<String> out = new ArrayList<>(rows.size());
        for (SectionRow row : rows) {
            out.add(row.raw() == null ? joinFrom(row, 0) : row.raw().trim());
        }
        return out;
    }

    /**
     * CONTROLS.rule: 규칙 블록(RULES 섹션)을 {@code {id, content}} 로 만든다.
     *
     * <p>편집을 문자열로 하기 위해 본문(IF/AND/THEN/PRIORITY 절)을 원본 라인 그대로 한 문자열(개행 구분)로
     * 담는다. {@code RULE <id>} 헤더 행과 주석 전용 줄({@code ; ...})은 제외된다.</p>
     */
    private List<Map<String, Object>> rules(List<RuleBlock> blocks) {
        List<Map<String, Object>> out = new ArrayList<>(blocks.size());
        for (RuleBlock block : blocks) {
            StringBuilder content = new StringBuilder();
            for (SectionRow clause : block.clauses()) {
                if (content.length() > 0) {
                    content.append('\n');
                }
                content.append(clause.raw() == null ? "" : clause.raw().stripTrailing());
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", block.id());
            rule.put("content", content.toString());
            out.add(rule);
        }
        return out;
    }

    /**
     * LABELS: {@code X Y "Text" [anchorNode]} 를 Label Editor 필드로 매핑한다.
     *
     * <p>INP 의 라벨 라인은 좌표·텍스트·(선택)앵커노드만 담는다. Meter Type/Meter ID/Font 는 EPANET GUI
     * 전용 속성으로 INP 에 없어 노출하지 않는다(프론트가 기본값 표시). 좌표는 차트/편집용 숫자(Double).</p>
     */
    private List<Map<String, Object>> labels(List<SectionRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (SectionRow row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", row.token(2));        // 라벨 텍스트(따옴표 제거됨)
            m.put("x", number(row.token(0)));    // X 좌표(투영좌표)
            m.put("y", number(row.token(1)));    // Y 좌표
            m.put("anchorNode", row.token(3));   // 앵커 노드(없으면 null)
            out.add(m);
        }
        return out;
    }

    // ===== 공통 유틸 =====

    /** TITLE: 자유 텍스트 라인 목록(행의 첫 토큰 = 트림된 라인 전체). */
    private List<String> titleLines(List<SectionRow> rows) {
        List<String> lines = new ArrayList<>(rows.size());
        for (SectionRow row : rows) {
            lines.add(row.id());
        }
        return lines;
    }

    /** 미지 섹션 폴백: 원본 라인(트림) 목록. */
    private List<String> rawLines(List<SectionRow> rows) {
        List<String> lines = new ArrayList<>(rows.size());
        for (SectionRow row : rows) {
            lines.add(row.raw() == null ? null : row.raw().trim());
        }
        return lines;
    }

    /** {@code start} 이후 토큰을 공백으로 이어 값 문자열을 만든다(없으면 빈 문자열). */
    private String joinFrom(SectionRow row, int start) {
        if (start >= row.size()) {
            return "";
        }
        return String.join(" ", row.tokens().subList(start, row.size()));
    }

    /** 숫자로 파싱 가능하면 Double, 아니면 원본 문자열(null 은 그대로). */
    private Object number(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }
}
