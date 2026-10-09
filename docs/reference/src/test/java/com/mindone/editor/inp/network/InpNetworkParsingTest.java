package com.mindone.editor.inp.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.combiner.NonVisualSectionCombiner;
import com.mindone.editor.inp.network.combiner.OptionsCombiner;
import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.geojson.GeoJsonFeature;
import com.mindone.editor.inp.network.geojson.GeoJsonGeometry;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.intake.InpReadResult;
import com.mindone.editor.inp.network.model.NetworkModel;
import com.mindone.editor.inp.network.model.NetworkNode;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 샘플 INP({@code gs_test_m_rule}) 기준 reader → parser → assembler → combiner 파이프라인 검증.
 *
 * <p>Spring 컨텍스트 없이 순수 컴포넌트를 직접 호출해 빠르게 검증한다. 기대값은 원본 파일에서 직접
 * 집계한 수치다(JUNCTIONS 513, PIPES 508, COORDINATES 515, VERTICES 10366, LABELS 87 등).</p>
 */
class InpNetworkParsingTest {

    /** "고산분기" — CP949 디코딩이 정상이면 첫 절점 ID 가 이 한글과 일치해야 한다(컴파일 인코딩 UTF-8 고정). */
    private static final String FIRST_JUNCTION_ID = "고산분기";

    private static byte[] sampleBytes;

    private final InpFileReader reader = new InpFileReader();
    private final InpParser parser = new InpParser();
    private final NetworkAssembler assembler = new NetworkAssembler();
    private final GeoJsonCombiner combiner = new GeoJsonCombiner();
    private final NonVisualSectionCombiner sectionCombiner = new NonVisualSectionCombiner();
    private final OptionsCombiner optionsCombiner = new OptionsCombiner();

    @BeforeAll
    static void loadSample() throws IOException {
        try (InputStream in = InpNetworkParsingTest.class.getResourceAsStream("/sample/network-sample.inp")) {
            assertThat(in).as("테스트 리소스 network-sample.inp 존재").isNotNull();
            sampleBytes = in.readAllBytes();
        }
    }

    @Test
    @DisplayName("인코딩 자동판별: CP949(MS949)로 디코딩되어 한글이 깨지지 않는다")
    void detectsCp949AndDecodesKorean() {
        InpReadResult read = reader.read(sampleBytes);

        assertThat(read.charsetName()).containsIgnoringCase("949"); // MS949 = x-windows-949
        assertThat(read.lines()).isNotEmpty();
        // U+FFFD(치환문자)가 라인에 없어야 손실 없는 디코딩
        assertThat(read.lines().stream().anyMatch(l -> l.indexOf('�') >= 0)).isFalse();
    }

    @Test
    @DisplayName("파서: 섹션별 데이터 행 수가 원본 집계와 일치한다(전 섹션 완전 파싱)")
    void parsesAllSectionsWithExpectedRowCounts() {
        ParsedInp parsed = parser.parse(reader.read(sampleBytes).lines());

        assertThat(parsed.rows(InpSectionType.JUNCTIONS)).hasSize(513);
        assertThat(parsed.rows(InpSectionType.RESERVOIRS)).hasSize(2);
        assertThat(parsed.rows(InpSectionType.TANKS)).isEmpty();
        assertThat(parsed.rows(InpSectionType.PIPES)).hasSize(508);
        assertThat(parsed.rows(InpSectionType.PUMPS)).hasSize(13);
        assertThat(parsed.rows(InpSectionType.VALVES)).hasSize(10);
        assertThat(parsed.rows(InpSectionType.COORDINATES)).hasSize(515);
        assertThat(parsed.rows(InpSectionType.VERTICES)).hasSize(10366);
        assertThat(parsed.rows(InpSectionType.LABELS)).hasSize(87);
        // 비가시 섹션도 보존된다
        assertThat(parsed.rows(InpSectionType.CURVES)).hasSize(296);
        assertThat(parsed.rows(InpSectionType.OPTIONS)).hasSize(18);
        // RULES 는 다중 라인 블록 — 데이터 행 120개가 8개 RULE 블록으로 묶인다
        assertThat(parsed.rows(InpSectionType.RULES)).hasSize(120);
        assertThat(parsed.ruleBlocks()).hasSize(8);
        // 미지 섹션은 없어야 한다(표준 섹션만 등장)
        assertThat(parsed.unknownSections()).isEmpty();
    }

    @Test
    @DisplayName("어셈블러: 노드/링크/라벨 수와 한글 ID·좌표 결합이 정확하다")
    void assemblesModelWithCoordinates() {
        NetworkModel model = assembler.assemble(parser.parse(reader.read(sampleBytes).lines()));

        assertThat(model.getNodes()).hasSize(515);   // 513 + 2 + 0
        assertThat(model.getLinks()).hasSize(531);    // 508 + 13 + 10
        assertThat(model.getLabels()).hasSize(87);

        // 첫 절점 = "고산분기", 좌표 결합 확인
        NetworkNode first = model.getNodes().get(0);
        assertThat(first.getId()).isEqualTo(FIRST_JUNCTION_ID);
        assertThat(first.hasCoord()).isTrue();

        // 모든 노드가 좌표를 가진다(COORDINATES 515 = 노드 515) → 좌표 누락 경고 없음
        assertThat(model.getNodes().stream().filter(n -> !n.hasCoord()).count()).isZero();

        // 옵션/경계 확인
        assertThat(model.getOptions().flowUnits()).isEqualTo("CMH");
        assertThat(model.getOptions().headloss()).isEqualTo("H-W");
        assertThat(model.getOptions().demandModel()).isEqualTo("PDA");
        assertThat(model.getBounds()).isNotNull();
        assertThat(model.getBackdropBounds()).isNotNull();
    }

    @Test
    @DisplayName("컴바이너: 노드/링크/라벨 3개 레이어로 그룹핑되고 링크 LineString 형상이 정확하다")
    void combinesIntoLayers() {
        NetworkModel model = assembler.assemble(parser.parse(reader.read(sampleBytes).lines()));
        NetworkLayers layers = combiner.toLayers(model);

        // 노드 레이어 = 절점 513 + 저수지 2 + 탱크 0, 링크 레이어 = 관로 508 + 펌프 13 + 밸브 10
        assertThat(layers.nodeLayer().features()).hasSize(515);
        assertThat(layers.linkLayer().features()).hasSize(531);
        assertThat(layers.labelLayer().features()).hasSize(87);

        // 노드 레이어 첫 Feature(절점) 는 Point 기하, 세부 타입은 속성으로 구분
        GeoJsonFeature firstJunction = layers.nodeLayer().features().get(0);
        assertThat(firstJunction.geometry()).isNotNull();
        assertThat(firstJunction.geometry().type()).isEqualTo("Point");
        assertThat(firstJunction.properties()).containsEntry("objectType", "junction");
        assertThat(firstJunction.properties()).containsEntry("layer", "junctions");

        // 관로 "28" 은 node1 → 정점 5개 → node2 = 좌표 7개의 LineString
        GeoJsonFeature pipe28 = layers.linkLayer().features().stream()
                .filter(f -> "28".equals(f.properties().get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(pipe28.geometry()).isNotNull();
        assertThat(pipe28.geometry().type()).isEqualTo("LineString");
        double[][] coords = (double[][]) pipe28.geometry().coordinates();
        assertThat(coords).hasNumberOfRows(7);

        // 펌프 "New_Pump#2": 여러 섹션이 한 객체로 통합된다(매뉴얼 6.4 속성편집기와 동일)
        GeoJsonFeature newPump2 = layers.linkLayer().features().stream()
                .filter(f -> "New_Pump#2".equals(f.properties().get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(newPump2.properties())
                .containsEntry("objectType", "pump")
                .containsEntry("pumpCurve", "New_Pump#2_HEAD")        // [PUMPS] HEAD 곡선 → pumpCurve
                .containsEntry("initialStatus", "CLOSED")             // [STATUS] 오버라이드(6.4 Initial Status) — enum 대문자 통일
                .containsEntry("efficiencyCurve", "New_Pump#2_eff");  // [ENERGY] 펌프별 효율곡선 병합
    }

    @Test
    @DisplayName("직렬화: 컨트롤러와 동일한 Jackson 경로로 유효한 GeoJSON 으로 직렬화된다")
    void serializesToValidGeoJson() throws Exception {
        NetworkModel model = assembler.assemble(parser.parse(reader.read(sampleBytes).lines()));
        NetworkLayers layers = combiner.toLayers(model);

        // 컨트롤러/응답이 사용하는 것과 동일한 Jackson 으로 직렬화 → 파싱 라운드트립
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(layers);
        JsonNode root = mapper.readTree(json);

        // 노드 레이어: FeatureCollection / Point / [x, y] 숫자 좌표
        JsonNode nodeLayer = root.get("nodeLayer");
        assertThat(nodeLayer.get("type").asText()).isEqualTo("FeatureCollection");
        JsonNode firstNodeGeom = nodeLayer.get("features").get(0).get("geometry");
        assertThat(firstNodeGeom.get("type").asText()).isEqualTo("Point");
        assertThat(firstNodeGeom.get("coordinates").isArray()).isTrue();
        assertThat(firstNodeGeom.get("coordinates").get(0).isNumber()).isTrue();

        // 링크 레이어: LineString / 중첩 좌표 배열([[x,y],...])
        JsonNode firstPipeGeom = root.get("linkLayer").get("features").get(0).get("geometry");
        assertThat(firstPipeGeom.get("type").asText()).isEqualTo("LineString");
        JsonNode lineCoords = firstPipeGeom.get("coordinates");
        assertThat(lineCoords.isArray()).isTrue();
        assertThat(lineCoords.get(0).isArray()).isTrue();
        assertThat(lineCoords.get(0).get(0).isNumber()).isTrue();
    }

    @Test
    @DisplayName("비가시 섹션: CURVES 는 사용처 기반 타입/설명/점, 제어는 CONTROLS{simple,rule} 로 묶인다(설정·객체병합 섹션 제외)")
    @SuppressWarnings("unchecked")
    void combinesNonVisualSections() {
        ParsedInp parsed = parser.parse(reader.read(sampleBytes).lines());
        Map<String, Object> sections = sectionCombiner.combine(parsed);

        // 가시 객체·지도·설정(options 로 분리)·객체병합(노드/링크 properties 로) 섹션은 제외된다
        assertThat(sections).doesNotContainKeys(
                "JUNCTIONS", "RESERVOIRS", "TANKS", "PIPES", "PUMPS", "VALVES",   // 가시 객체
                "COORDINATES", "VERTICES", "BACKDROP", "END",                      // 지도/좌표
                "OPTIONS", "TIMES", "REACTIONS", "ENERGY",                          // 설정(OptionsCombiner 로 분리, 서비스가 OPTIONS 키로 합류)
                "RULES",                                                           // 규칙 제어(CONTROLS.rule 로 묶임)
                "TAGS", "DEMANDS", "EMITTERS", "QUALITY", "SOURCES", "MIXING", "STATUS"); // 객체 properties 로 병합

        // CURVES: 곡선 ID별 {id, curveType, description, xyData} — 타입은 사용처 기반 추론(권위), 설명은 직전 주석
        List<Map<String, Object>> curves = (List<Map<String, Object>>) sections.get("CURVES");
        Map<String, Object> effCurve = curves.stream()
                .filter(c -> "Old_Pump#1_eff".equals(c.get("id")))
                .findFirst().orElseThrow();
        assertThat(effCurve.get("curveType")).isEqualTo("EFFICIENCY");           // [ENERGY] EFFIC 로 참조됨 → 사용처 추론
        assertThat(effCurve.get("description")).isEqualTo("EFFICIENCY: Old_Pump#1_eff");
        List<Map<String, Object>> xyData = (List<Map<String, Object>>) effCurve.get("xyData");
        assertThat(xyData).hasSize(5);
        assertThat(xyData.get(0).get("x")).isInstanceOf(Double.class);
        assertThat(xyData.get(0).get("y")).isInstanceOf(Double.class);

        // CONTROLS: 단순 제어(simple) + 규칙 제어(rule) 한 묶음
        Map<String, Object> controls = (Map<String, Object>) sections.get("CONTROLS");
        assertThat(controls).containsKeys("simple", "rule");

        // CONTROLS.rule: RULE 아이디별 {id, content} — content 는 본문을 한 문자열로
        List<Map<String, Object>> rules = (List<Map<String, Object>>) controls.get("rule");
        assertThat(rules).hasSize(8);
        Map<String, Object> rule1 = rules.get(0);
        assertThat(rule1.get("id")).isEqualTo("1");
        String content = (String) rule1.get("content");
        assertThat(content).contains("IF Pipe 10 flow >= 18000")
                .contains("THEN Pump Old_Pump#2 status = OPEN")
                .contains("PRIORITY 100");
        assertThat(content.lines().count()).isEqualTo(14); // IF + AND + THEN + 10 AND + PRIORITY

        // CONTROLS.simple: 단순 제어문 원본 텍스트 라인 목록(EPANET Controls Editor = 텍스트 편집기). 샘플은 비어 빈 목록
        assertThat(controls.get("simple")).isInstanceOf(List.class);
        assertThat((List<?>) controls.get("simple")).allSatisfy(c -> assertThat(c).isInstanceOf(String.class));

        // LABELS: {text, x, y, anchorNode} — Label Editor 대응(좌표는 숫자)
        List<Map<String, Object>> labels = (List<Map<String, Object>>) sections.get("LABELS");
        assertThat(labels).hasSize(87);
        Map<String, Object> firstLabel = labels.get(0);
        assertThat(firstLabel.get("text")).isEqualTo("감압밸브"); // CP949 디코딩 정상
        assertThat(firstLabel.get("x")).isInstanceOf(Double.class);
        assertThat(firstLabel.get("y")).isInstanceOf(Double.class);
        assertThat(firstLabel).containsKey("anchorNode");

        // TITLE: 자유 텍스트 라인 목록(빈 섹션이면 빈 리스트)
        assertThat(sections.get("TITLE")).isInstanceOf(List.class);

        // 미지 섹션은 없다(표준 섹션만 등장)
        assertThat(sections.keySet().stream().anyMatch(k -> k.startsWith("UNKNOWN:"))).isFalse();
    }

    @Test
    @DisplayName("CONTROLS 스키마 고정: 원본에 CONTROLS/RULES 가 없거나 한쪽만 있어도 {simple, rule} 이 항상 나온다")
    @SuppressWarnings("unchecked")
    void controlsKeyIsAlwaysPresent() {
        // (a) 두 섹션 모두 없는 INP — 프론트가 sections.CONTROLS.simple 로 바로 바인딩할 수 있어야 한다
        Map<String, Object> none = sectionCombiner.combine(parse("""
                [TITLE]
                제어 없는 관망

                [JUNCTIONS]
                J1  10  0

                [END]
                """));
        Map<String, Object> noneControls = (Map<String, Object>) none.get("CONTROLS");
        assertThat(noneControls).containsOnlyKeys("simple", "rule");
        assertThat((List<?>) noneControls.get("simple")).isEmpty();
        assertThat((List<?>) noneControls.get("rule")).isEmpty();

        // (b) RULES 만 있는 INP — simple 은 빈 목록, rule 에 규칙이 담긴다
        Map<String, Object> onlyRules = sectionCombiner.combine(parse("""
                [TITLE]
                규칙만 있는 관망

                [RULES]
                RULE 1
                IF SYSTEM TIME > 5
                THEN PIPE P1 STATUS = CLOSED

                [END]
                """));
        Map<String, Object> ruleControls = (Map<String, Object>) onlyRules.get("CONTROLS");
        assertThat((List<?>) ruleControls.get("simple")).isEmpty();
        assertThat((List<Map<String, Object>>) ruleControls.get("rule")).hasSize(1);
        assertThat(((List<Map<String, Object>>) ruleControls.get("rule")).get(0).get("id")).isEqualTo("1");

        // 별도 RULES 키는 어느 경우에도 노출되지 않는다
        assertThat(none).doesNotContainKey("RULES");
        assertThat(onlyRules).doesNotContainKey("RULES");
    }

    @Test
    @DisplayName("설정(Options): EPANET Options 브라우저 5범주(수리/수질/반응/시간/에너지)로 묶인다")
    @SuppressWarnings("unchecked")
    void combinesOptions() {
        ParsedInp parsed = parser.parse(reader.read(sampleBytes).lines());
        Map<String, Object> options = optionsCombiner.combine(parsed);

        // Hydraulics: 친화 키 + 두 단어 키 + 다중 토큰 값
        Map<String, Object> hydraulics = (Map<String, Object>) options.get("hydraulics");
        assertThat(hydraulics).containsEntry("flowUnits", "CMH");
        assertThat(hydraulics).containsEntry("headlossFormula", "H-W");
        assertThat(hydraulics).containsEntry("demandModel", "PDA");
        assertThat(hydraulics).containsEntry("ifUnbalanced", "CONTINUE 10"); // 다중 토큰 값
        assertThat(hydraulics).containsEntry("maximumTrials", "40");

        // Quality: [OPTIONS] 의 QUALITY/DIFFUSIVITY/TOLERANCE
        Map<String, Object> quality = (Map<String, Object>) options.get("quality");
        assertThat(quality).containsEntry("parameter", "NONE");
        assertThat(quality).containsEntry("relativeDiffusivity", "1");
        assertThat(quality).containsEntry("qualityTolerance", "0.01");

        // Reactions: 차수/전역계수/한계농도/벽계수상관(8.1 라벨)
        Map<String, Object> reactions = (Map<String, Object>) options.get("reactions");
        assertThat(reactions).containsEntry("bulkReactionOrder", "1");
        assertThat(reactions).containsEntry("wallReactionOrder", "1");
        assertThat(reactions).containsEntry("tankReactionOrder", "1");
        assertThat(reactions).containsEntry("globalBulkCoefficient", "0.0000");
        assertThat(reactions).containsEntry("limitingConcentration", "0.0000");
        assertThat(reactions).containsEntry("wallCoefficientCorrelation", "0.0000");

        // Times: 친화 키(8.1 라벨), 시각 값 보존
        Map<String, Object> times = (Map<String, Object>) options.get("times");
        assertThat(times).containsEntry("totalDuration", "01:00:00");
        assertThat(times).containsEntry("hydraulicTimeStep", "00:01:00");
        assertThat(times).containsEntry("startingTimeOfDay", "00:00:00 AM");
        assertThat(times).containsEntry("statistic", "NONE");

        // Energy: EPANET Energy 패널의 전역 항목만(효율/단가/수요요금). 펌프별 효율곡선은 펌프 링크 properties 소관.
        Map<String, Object> energy = (Map<String, Object>) options.get("energy");
        assertThat(energy).containsEntry("pumpEfficiency", "85.0000");
        assertThat(energy).containsEntry("energyPrice", "0.0000");
        assertThat(energy).containsEntry("demandCharge", "0.0000");
        assertThat(energy).doesNotContainKey("pumps"); // 펌프별 항목은 energy 가 아닌 펌프 객체에 병합된다
    }

    @Test
    @DisplayName("설정 전체 스키마 고정: INP 에 없는 항목도 키는 항상 노출되고 값만 null 이다")
    @SuppressWarnings("unchecked")
    void optionsExposeFullSchemaWithNulls() {
        Map<String, Object> options = optionsCombiner.combine(parser.parse(reader.read(sampleBytes).lines()));

        // energy: EPANET Energy 패널의 4개 전역 항목이 항상 있다. 샘플에는 GLOBAL PATTERN 줄이 없어 pricePattern 은 null.
        Map<String, Object> energy = (Map<String, Object>) options.get("energy");
        assertThat(energy).containsOnlyKeys("pumpEfficiency", "energyPrice", "pricePattern", "demandCharge");
        assertThat(energy.get("pricePattern")).isNull();
        assertThat(energy.get("pumpEfficiency")).isEqualTo("85.0000");

        // hydraulics/quality/reactions/times 도 전체 항목을 노출한다(없는 값은 null)
        assertThat((Map<String, Object>) options.get("hydraulics"))
                .containsKeys("flowUnits", "headlossFormula", "specificGravity", "relativeViscosity",
                        "maximumTrials", "accuracy", "ifUnbalanced", "defaultPattern", "demandMultiplier",
                        "emitterExponent", "demandModel", "minimumPressure", "requiredPressure",
                        "pressureExponent", "checkFreq", "maxCheck", "dampLimit", "maxHeadError", "maxFlowChange");
        assertThat((Map<String, Object>) options.get("quality"))
                .containsKeys("parameter", "massUnits", "traceNode", "relativeDiffusivity", "qualityTolerance");
        // reactions 는 EPANET Reactions 패널의 전역 설정만 — 요소별 계수는 객체 properties 소관이라 없다
        assertThat((Map<String, Object>) options.get("reactions"))
                .containsOnlyKeys("bulkReactionOrder", "wallReactionOrder", "tankReactionOrder",
                        "globalBulkCoefficient", "globalWallCoefficient", "limitingConcentration",
                        "wallCoefficientCorrelation");
        assertThat((Map<String, Object>) options.get("times"))
                .containsKeys("totalDuration", "hydraulicTimeStep", "qualityTimeStep", "patternTimeStep",
                        "patternStartTime", "reportingTimeStep", "reportStartTime", "startingTimeOfDay",
                        "ruleTimeStep", "statistic");

        // 수질 파라미터가 NONE 이면 massUnits/traceNode 는 둘 다 null(택일 항목)
        Map<String, Object> quality = (Map<String, Object>) options.get("quality");
        assertThat(quality.get("parameter")).isEqualTo("NONE");
        assertThat(quality.get("massUnits")).isNull();
        assertThat(quality.get("traceNode")).isNull();
    }

    @Test
    @DisplayName("설정 전체 스키마 고정: 표준 외 키워드도 유실 없이 스키마 키 뒤에 보존된다")
    @SuppressWarnings("unchecked")
    void optionsPreserveNonStandardKeywords() {
        Map<String, Object> options = optionsCombiner.combine(parse("""
                [OPTIONS]
                UNITS        CMH
                FOOBAR       7

                [END]
                """));

        Map<String, Object> hydraulics = (Map<String, Object>) options.get("hydraulics");
        assertThat(hydraulics.get("flowUnits")).isEqualTo("CMH");
        assertThat(hydraulics.get("FOOBAR")).isEqualTo("7");        // 표준 외 키워드 보존
        assertThat(hydraulics.get("specificGravity")).isNull();      // 없는 표준 항목은 null
        // 스키마 키가 먼저, 표준 외 키가 뒤에 온다
        assertThat(hydraulics.keySet()).last().isEqualTo("FOOBAR");
    }

    @Test
    @DisplayName("직렬화: 응답과 동일한 Jackson 경로로 sections/options 가 직렬화된다")
    void serializesNonVisualSectionsAndOptions() throws Exception {
        ParsedInp parsed = parser.parse(reader.read(sampleBytes).lines());
        Map<String, Object> sections = sectionCombiner.combine(parsed);
        Map<String, Object> options = optionsCombiner.combine(parsed);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sectionsNode = mapper.readTree(mapper.writeValueAsString(sections));
        JsonNode optionsNode = mapper.readTree(mapper.writeValueAsString(options));

        // CURVES: 배열, 각 곡선 {id, curveType, description, xyData:[{x,y}]}, x/y 는 숫자
        JsonNode firstCurve = sectionsNode.get("CURVES").get(0);
        assertThat(firstCurve.has("curveType")).isTrue();
        assertThat(firstCurve.get("xyData").get(0).get("x").isNumber()).isTrue();

        // CONTROLS.rule: 배열, {id, content} — content 는 문자열
        JsonNode rule1 = sectionsNode.get("CONTROLS").get("rule").get(0);
        assertThat(rule1.get("id").asText()).isEqualTo("1");
        assertThat(rule1.get("content").isTextual()).isTrue();

        // OPTIONS: 5범주 객체로 직렬화
        assertThat(optionsNode.get("hydraulics").get("flowUnits").asText()).isEqualTo("CMH");
        assertThat(optionsNode.get("energy").get("pumpEfficiency").asText()).isEqualTo("85.0000");
    }

    /** 인라인 INP 텍스트를 실제 파이프라인(CP949 디코딩 포함)으로 파싱한다. */
    private ParsedInp parse(String inp) {
        return parser.parse(reader.read(inp.getBytes(java.nio.charset.Charset.forName("MS949"))).lines());
    }
}
