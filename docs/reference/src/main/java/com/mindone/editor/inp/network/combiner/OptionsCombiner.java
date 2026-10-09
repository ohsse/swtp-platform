package com.mindone.editor.inp.network.combiner;

import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.parser.SectionRow;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * INP 의 설정 섹션([OPTIONS]/[TIMES]/[REACTIONS]/[ENERGY])을 EPANET <b>Options 브라우저</b> 구조로 묶는다.
 *
 * <p>EPANET 의 Options 패널(이미지: Hydraulics/Quality/Reactions/Times/Energy)에 맞춰, 각 항목을 친화
 * 키({@code flowUnits}, {@code headlossFormula} …) → 값(문자열)으로 노출한다. [OPTIONS] 섹션은 UI 처럼
 * 수리(Hydraulics)와 수질(Quality) 두 범주로 나뉜다.</p>
 *
 * <pre>
 * options: {
 *   hydraulics: { flowUnits, headlossFormula, specificGravity, ..., maximumTrials, ... },
 *   quality:    { parameter, massUnits?, traceNode?, relativeDiffusivity, qualityTolerance },
 *   reactions:  { bulkReactionOrder, wallReactionOrder, tankReactionOrder, globalBulkCoefficient,
 *                 globalWallCoefficient, limitingConcentration, wallCoefficientCorrelation },
 *   times:      { totalDuration, hydraulicTimeStep, ..., startingTimeOfDay, statistic },
 *   energy:     { pumpEfficiency, energyPrice, pricePattern, demandCharge }
 * }
 * </pre>
 *
 * <p>친화 키는 매뉴얼 8.1(Analysis Options)의 라벨을 camelCase 로 옮긴 것이다(예: Maximum Trials→{@code
 * maximumTrials}, Limiting Concentration→{@code limitingConcentration}, Wall Coefficient Correlation→
 * {@code wallCoefficientCorrelation}, Starting Time of Day→{@code startingTimeOfDay}).</p>
 *
 * <p>값은 표시/편집용 문자열 그대로 둔다(시각의 {@code 01:00:00}, 차수 {@code 1} 등 UI 변환은 프론트 몫).</p>
 *
 * <p><b>전체 스키마 고정</b>: 각 범주는 EPANET Options 브라우저의 전체 항목을 <b>항상 같은 키·같은 순서</b>로
 * 노출하고, INP 에 해당 줄이 없으면 값만 {@code null} 이다.
 * 가시 객체 properties 와 동일한 방침으로, 프론트가 키 유무 검사 없이 폼을 바인딩할 수 있다. 저장 시
 * {@code null} 은 {@link com.mindone.editor.inp.network.compose.InpComposer} 가 건너뛰므로 원본에 없던
 * INP 줄이 새로 생기지 않는다. 표준 외 키워드는 스키마 키 뒤에 유실 없이 이어 붙인다.</p>
 *
 * <p><b>펌프별 [ENERGY] 항목은 여기서 다루지 않는다</b>: EPANET Options 브라우저의 Energy 패널(매뉴얼 8.1)은
 * 전역 항목({@code pumpEfficiency}/{@code energyPrice}/{@code pricePattern}/{@code demandCharge})만 보여준다.
 * {@code PUMP <id> EFFIC/PRICE/PATTERN} 줄(펌프별 효율곡선·단가·단가패턴)은 EPANET 펌프 속성편집기(매뉴얼 6.4,
 * Table 6.5)의 항목이라, {@link NetworkAssembler} 가 해당 펌프 링크의 {@code efficiencyCurve}/{@code energyPrice}/
 * {@code pricePattern} 속성으로 이미 병합한다. 저장 시 {@link com.mindone.editor.inp.network.compose.InpComposer}
 * 도 그 펌프 속성에서 행을 되만든다. 즉 펌프가 단일 출처이며, 여기에 다시 실으면 같은 값이 두 곳에 존재해
 * 어긋날 수 있다(REACTIONS 요소별 계수와 동일한 방침).</p>
 */
@Component
public class OptionsCombiner {

    /** [OPTIONS] 의 두 단어(2토큰) 키. */
    private static final Set<String> OPTION_TWO_WORD_KEYS = Set.of(
            "SPECIFIC GRAVITY", "DEMAND MULTIPLIER", "DEMAND MODEL", "MINIMUM PRESSURE",
            "REQUIRED PRESSURE", "PRESSURE EXPONENT", "EMITTER EXPONENT"
    );

    /** [OPTIONS] 수리 키워드 → 친화 키. */
    private static final Map<String, String> HYDRAULIC_KEYS = Map.ofEntries(
            Map.entry("UNITS", "flowUnits"),
            Map.entry("HEADLOSS", "headlossFormula"),
            Map.entry("SPECIFIC GRAVITY", "specificGravity"),
            Map.entry("VISCOSITY", "relativeViscosity"),
            Map.entry("TRIALS", "maximumTrials"),
            Map.entry("ACCURACY", "accuracy"),
            Map.entry("UNBALANCED", "ifUnbalanced"),
            Map.entry("PATTERN", "defaultPattern"),
            Map.entry("DEMAND MULTIPLIER", "demandMultiplier"),
            Map.entry("EMITTER EXPONENT", "emitterExponent"),
            Map.entry("DEMAND MODEL", "demandModel"),
            Map.entry("MINIMUM PRESSURE", "minimumPressure"),
            Map.entry("REQUIRED PRESSURE", "requiredPressure"),
            Map.entry("PRESSURE EXPONENT", "pressureExponent"),
            Map.entry("CHECKFREQ", "checkFreq"),
            Map.entry("MAXCHECK", "maxCheck"),
            Map.entry("DAMPLIMIT", "dampLimit"),
            Map.entry("HEADERROR", "maxHeadError"),
            Map.entry("FLOWCHANGE", "maxFlowChange")
    );

    /** [OPTIONS] 중 수질 범주로 분류되는 키워드. */
    private static final Set<String> QUALITY_KEYWORDS = Set.of("QUALITY", "DIFFUSIVITY", "TOLERANCE");

    /**
     * 콤보박스 순수 enum 값이라 대문자로 통일하는 범주별 키(값이 대소문자 무시 키워드인 필드만).
     * ID·단위·자유값(defaultPattern/massUnits/traceNode 등)은 대소문자를 구분하므로 제외한다.
     */
    private static final Set<String> HYDRAULIC_ENUM_KEYS = Set.of(
            "flowUnits", "headlossFormula", "ifUnbalanced", "demandModel");
    private static final Set<String> QUALITY_ENUM_KEYS = Set.of("parameter");
    private static final Set<String> TIME_ENUM_KEYS = Set.of("statistic");

    /** [TIMES] 키워드 → 친화 키(8.1 Times Options 라벨 기준). */
    private static final Map<String, String> TIME_KEYS = Map.ofEntries(
            Map.entry("DURATION", "totalDuration"),              // Total Duration
            Map.entry("HYDRAULIC TIMESTEP", "hydraulicTimeStep"), // Hydraulic Time Step
            Map.entry("QUALITY TIMESTEP", "qualityTimeStep"),    // Quality Time Step
            Map.entry("PATTERN TIMESTEP", "patternTimeStep"),    // Pattern Time Step
            Map.entry("PATTERN START", "patternStartTime"),      // Pattern Start Time
            Map.entry("REPORT TIMESTEP", "reportingTimeStep"),   // Reporting Time Step
            Map.entry("REPORT START", "reportStartTime"),        // Report Start Time
            Map.entry("START CLOCKTIME", "startingTimeOfDay"),   // Starting Time of Day
            Map.entry("RULE TIMESTEP", "ruleTimeStep"),          // Rule Time Step(INP 전용)
            Map.entry("STATISTIC", "statistic")                  // Statistic
    );

    /** [TIMES] 의 두 단어(2토큰) 키. */
    private static final Set<String> TIME_TWO_WORD_KEYS = Set.of(
            "HYDRAULIC TIMESTEP", "QUALITY TIMESTEP", "PATTERN TIMESTEP", "PATTERN START",
            "REPORT TIMESTEP", "REPORT START", "RULE TIMESTEP", "START CLOCKTIME"
    );

    // ===== 전체 스키마(고정 키 순서) =====
    // EPANET Options 브라우저(매뉴얼 8.1)의 범주별 전체 항목. INP 에 없어도 키는 노출하고 값만 null 이다.

    /** Hydraulics 범주 전체 키(8.1 Hydraulic Options 순서). */
    private static final List<String> HYDRAULIC_SCHEMA = List.of(
            "flowUnits", "headlossFormula", "specificGravity", "relativeViscosity",
            "maximumTrials", "accuracy", "ifUnbalanced", "defaultPattern",
            "demandMultiplier", "emitterExponent", "demandModel", "minimumPressure",
            "requiredPressure", "pressureExponent", "checkFreq", "maxCheck",
            "dampLimit", "maxHeadError", "maxFlowChange"
    );

    /** Quality 범주 전체 키({@code massUnits}/{@code traceNode} 는 parameter 에 따라 택일). */
    private static final List<String> QUALITY_SCHEMA = List.of(
            "parameter", "massUnits", "traceNode", "relativeDiffusivity", "qualityTolerance"
    );

    /** Reactions 범주 전체 키 — EPANET Reactions 패널의 전역 설정만(요소별 계수는 객체 properties 소관). */
    private static final List<String> REACTION_SCHEMA = List.of(
            "bulkReactionOrder", "wallReactionOrder", "tankReactionOrder",
            "globalBulkCoefficient", "globalWallCoefficient",
            "limitingConcentration", "wallCoefficientCorrelation"
    );

    /** Times 범주 전체 키(8.1 Times Options 순서). */
    private static final List<String> TIME_SCHEMA = List.of(
            "totalDuration", "hydraulicTimeStep", "qualityTimeStep", "patternTimeStep",
            "patternStartTime", "reportingTimeStep", "reportStartTime", "startingTimeOfDay",
            "ruleTimeStep", "statistic"
    );

    /** Energy 범주 전체 키 — EPANET Energy 패널의 전역 항목만(펌프별 항목은 펌프 링크 properties 소관). */
    private static final List<String> ENERGY_SCHEMA = List.of(
            "pumpEfficiency", "energyPrice", "pricePattern", "demandCharge"
    );

    /**
     * 설정 섹션을 Options 구조로 묶는다.
     *
     * @param parsed 무손실 파싱 결과
     * @return {@code {hydraulics, quality, reactions, times, energy}}
     */
    public Map<String, Object> combine(ParsedInp parsed) {
        Map<String, Object> hydraulics = new LinkedHashMap<>();
        Map<String, Object> quality = new LinkedHashMap<>();
        parseOptions(parsed.rows(InpSectionType.OPTIONS), hydraulics, quality);
        Map<String, Object> times = times(parsed.rows(InpSectionType.TIMES));

        // 콤보박스 순수 enum 값만 대문자로 통일(프론트 콤보박스 표기 일관성). ID·단위 등은 손대지 않는다.
        normalizeEnums(hydraulics, HYDRAULIC_ENUM_KEYS);
        normalizeEnums(quality, QUALITY_ENUM_KEYS);
        normalizeEnums(times, TIME_ENUM_KEYS);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("hydraulics", withSchema(hydraulics, HYDRAULIC_SCHEMA));
        options.put("quality", withSchema(quality, QUALITY_SCHEMA));
        options.put("reactions", withSchema(reactions(parsed.rows(InpSectionType.REACTIONS)), REACTION_SCHEMA));
        options.put("times", withSchema(times, TIME_SCHEMA));
        options.put("energy", withSchema(energy(parsed.rows(InpSectionType.ENERGY)), ENERGY_SCHEMA));
        return options;
    }

    /** 지정한 enum 키들의 값(문자열)을 대문자로 정규화한다. */
    private void normalizeEnums(Map<String, Object> category, Set<String> enumKeys) {
        for (String key : enumKeys) {
            EnumNormalizer.upperInPlace(category, key);
        }
    }

    /**
     * 범주 값을 <b>전체 스키마 고정</b> 형태로 정규화한다 — 스키마 키를 정해진 순서로 모두 노출하고,
     * INP 에 없던 항목의 값은 {@code null} 로 둔다.
     *
     * <p>프론트가 키 유무 검사 없이 폼을 바인딩할 수 있게 하려는 것으로, 가시 객체 properties 와 같은 방침이다
     * (예: {@code [ENERGY]} 에 {@code GLOBAL PATTERN} 줄이 없어도 {@code pricePattern: null} 이 나온다).
     * 저장 시 {@code null} 값은 {@link com.mindone.editor.inp.network.compose.InpComposer} 가 건너뛰므로
     * 원본에 없던 줄이 새로 생기지 않는다.</p>
     *
     * <p>스키마에 없는 키(표준 외 키워드)는 <b>유실 없이</b> 스키마 키 뒤에 원래 순서대로 이어 붙인다.</p>
     */
    private Map<String, Object> withSchema(Map<String, Object> values, List<String> schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : schema) {
            out.put(key, values.get(key)); // 없으면 null
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue()); // 표준 외 키워드/배열 보존
        }
        return out;
    }

    // ===== OPTIONS → 수리/수질 =====

    /** [OPTIONS] 행을 수리/수질 범주로 나눠 친화 키-밸류로 채운다. */
    private void parseOptions(List<SectionRow> rows, Map<String, Object> hydraulics, Map<String, Object> quality) {
        for (SectionRow row : rows) {
            if (row.size() == 0) {
                continue;
            }
            String twoWord = row.size() >= 2 ? (row.token(0) + " " + row.token(1)) : null;
            String keyword;
            int valueStart;
            if (twoWord != null && OPTION_TWO_WORD_KEYS.contains(twoWord.toUpperCase(Locale.ROOT))) {
                keyword = twoWord;
                valueStart = 2;
            } else {
                keyword = row.token(0);
                valueStart = 1;
            }
            String upper = keyword.toUpperCase(Locale.ROOT);
            if (QUALITY_KEYWORDS.contains(upper)) {
                putQuality(quality, upper, row, valueStart);
            } else {
                hydraulics.put(HYDRAULIC_KEYS.getOrDefault(upper, keyword), joinFrom(row, valueStart));
            }
        }
    }

    /** 수질 범주 항목 채우기. QUALITY 는 파라미터 + (질량단위 또는 추적노드)로 분해. */
    private void putQuality(Map<String, Object> quality, String keyword, SectionRow row, int valueStart) {
        switch (keyword) {
            case "QUALITY" -> {
                String type = row.token(valueStart);
                quality.put("parameter", type); // NONE/CHEMICAL/AGE/TRACE
                if ("CHEMICAL".equalsIgnoreCase(type) && row.token(valueStart + 1) != null) {
                    quality.put("massUnits", row.token(valueStart + 1));
                } else if ("TRACE".equalsIgnoreCase(type) && row.token(valueStart + 1) != null) {
                    quality.put("traceNode", row.token(valueStart + 1));
                }
            }
            case "DIFFUSIVITY" -> quality.put("relativeDiffusivity", joinFrom(row, valueStart));
            case "TOLERANCE" -> quality.put("qualityTolerance", joinFrom(row, valueStart));
            default -> { /* 도달하지 않음 */ }
        }
    }

    // ===== REACTIONS =====

    /**
     * [REACTIONS]: EPANET Options 브라우저의 Reactions 패널에 해당하는 <b>전역 항목만</b> 친화 키로 노출한다.
     *
     * <p>요소별 계수 행({@code BULK/WALL/TANK <id> <coef>})은 여기서 다루지 않는다. 이미
     * {@link NetworkAssembler} 가 해당 객체의 {@code bulkCoefficient}/{@code wallCoefficient}/
     * {@code reactionCoefficient} 속성으로 병합하고, 저장 시
     * {@link com.mindone.editor.inp.network.compose.InpComposer} 도 그 객체 속성에서 행을 되만든다.
     * 즉 객체가 단일 출처이며, 여기에 다시 실으면 같은 값이 두 곳에 존재해 어긋날 수 있다.</p>
     */
    private Map<String, Object> reactions(List<SectionRow> rows) {
        Map<String, Object> reactions = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            if (row.size() == 0) {
                continue;
            }
            switch (row.token(0).toUpperCase(Locale.ROOT)) {
                case "ORDER" -> reactions.put(orderKey(row.token(1)), row.token(2));
                case "GLOBAL" -> reactions.put(globalKey(row.token(1)), row.token(2));
                case "LIMITING" -> reactions.put("limitingConcentration", row.token(2));      // Limiting Concentration
                case "ROUGHNESS" -> reactions.put("wallCoefficientCorrelation", row.token(2)); // Wall Coefficient Correlation
                // BULK/WALL/TANK <id> <coef> 는 객체 properties 로 병합되므로 여기서 중복 노출하지 않는다
                default -> { /* 무시 */ }
            }
        }
        return reactions;
    }

    private String orderKey(String type) {
        if (type == null) {
            return "order";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BULK" -> "bulkReactionOrder";   // Bulk Reaction Order
            case "WALL" -> "wallReactionOrder";   // Wall Reaction Order
            case "TANK" -> "tankReactionOrder";   // (INP 전용 — GUI 는 Bulk 차수를 탱크에 적용)
            default -> "order_" + type;
        };
    }

    private String globalKey(String type) {
        if (type == null) {
            return "global";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BULK" -> "globalBulkCoefficient";   // Global Bulk Coefficient
            case "WALL" -> "globalWallCoefficient";   // Global Wall Coefficient
            default -> "global_" + type;
        };
    }

    // ===== TIMES =====

    /** [TIMES]: 친화 키-밸류 단일 맵(두 단어 키 / 다중 토큰 값 처리). */
    private Map<String, Object> times(List<SectionRow> rows) {
        Map<String, Object> times = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            if (row.size() == 0) {
                continue;
            }
            String twoWord = row.size() >= 2 ? (row.token(0) + " " + row.token(1)) : null;
            String keyword;
            int valueStart;
            if (twoWord != null && TIME_TWO_WORD_KEYS.contains(twoWord.toUpperCase(Locale.ROOT))) {
                keyword = twoWord;
                valueStart = 2;
            } else {
                keyword = row.token(0);
                valueStart = 1;
            }
            times.put(TIME_KEYS.getOrDefault(keyword.toUpperCase(Locale.ROOT), keyword), joinFrom(row, valueStart));
        }
        return times;
    }

    // ===== ENERGY =====

    /**
     * [ENERGY]: EPANET Energy 패널에 해당하는 <b>전역 항목만</b>(GLOBAL/DEMAND CHARGE) 친화 키로 노출한다.
     *
     * <p>펌프별 행({@code PUMP <id> EFFIC/PRICE/PATTERN})은 여기서 다루지 않는다. 이미
     * {@link NetworkAssembler} 가 해당 펌프 링크의 {@code efficiencyCurve}/{@code energyPrice}/
     * {@code pricePattern} 속성으로 병합하고, 저장 시
     * {@link com.mindone.editor.inp.network.compose.InpComposer} 도 그 펌프 속성에서 행을 되만든다.
     * 즉 펌프가 단일 출처이며, 여기에 다시 실으면 같은 값이 두 곳에 존재해 어긋날 수 있다(REACTIONS 요소별
     * 계수와 동일한 방침).</p>
     */
    private Map<String, Object> energy(List<SectionRow> rows) {
        Map<String, Object> energy = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            if (row.size() == 0) {
                continue;
            }
            switch (row.token(0).toUpperCase(Locale.ROOT)) {
                case "GLOBAL" -> energy.put(globalEnergyKey(row.token(1)), joinFrom(row, 2));
                case "DEMAND" -> energy.put("demandCharge", joinFrom(row, 2)); // DEMAND CHARGE <v>
                // PUMP <id> ... 는 펌프 링크 properties 로 병합되므로 여기서 중복 노출하지 않는다
                default -> { /* 무시 */ }
            }
        }
        return energy;
    }

    private String globalEnergyKey(String param) {
        if (param == null) {
            return "global";
        }
        return switch (param.toUpperCase(Locale.ROOT)) {
            case "EFFICIENCY" -> "pumpEfficiency";
            case "PRICE" -> "energyPrice";
            case "PATTERN" -> "pricePattern";
            default -> "global_" + param;
        };
    }

    // ===== 공통 =====

    /** {@code start} 이후 토큰을 공백으로 이어 값 문자열을 만든다(없으면 빈 문자열). */
    private String joinFrom(SectionRow row, int start) {
        if (start >= row.size()) {
            return "";
        }
        return String.join(" ", row.tokens().subList(start, row.size()));
    }
}
