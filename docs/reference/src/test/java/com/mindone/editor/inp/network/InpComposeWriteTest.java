package com.mindone.editor.inp.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.combiner.NonVisualSectionCombiner;
import com.mindone.editor.inp.network.combiner.OptionsCombiner;
import com.mindone.editor.inp.network.compose.InpComposer;
import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.model.NetworkModel;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.writer.InpWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composer → Writer 역흐름 검증 — {@code response.json}(상세조회 응답)을 편집 결과로 보고 INP 로 다시 쓴 뒤,
 * 동일 포워드 파이프라인으로 재파싱했을 때 <b>원본 INP 파싱 결과와 의미가 동일</b>함을 확인한다.
 *
 * <p><b>검증 기준(왜 바이트 단위 비교가 아닌가)</b>: 상세조회 응답은 가독성을 위한 손실 투영이라, 원본의
 * 파일 헤더 주석({@code ; Filename}/{@code ; WNTR}/{@code ; Created}), 섹션별 컬럼 주석, 정확한 컬럼 폭/탭,
 * 숫자 표기(좌표 9자리·{@code 0.0000} 등), 줄 끝 {@code ;} 같은 <i>비의미 정보</i>는 응답에 존재하지 않는다.
 * 따라서 응답만으로 원본 바이트를 복원할 수는 없다. 대신 "편집→저장→재조회"가 보장해야 하는 진짜 불변식은
 * <b>의미적 라운드트립</b>이다: {@code parse(write(json))} 가 {@code parse(원본)} 과 같은 네트워크를 산출한다.
 * 이 테스트가 그 불변식을 검증한다.</p>
 *
 * <p>부수적으로 직렬화 결과를 {@code doc/sample/composed_output.inp} 로 남겨 육안 비교가 가능하게 한다.</p>
 */
class InpComposeWriteTest {

    /** 응답을 역변환할 입력(상세조회 응답 = 편집 전 상태). */
    private static byte[] responseJson;
    /** 비교 기준이 될 원본 INP 바이트(테스트 리소스 = 타겟 파일과 바이트 동일). */
    private static byte[] originalInp;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 포워드 파이프라인(재파싱용) — 상세조회와 동일 컴포넌트
    private final InpFileReader reader = new InpFileReader();
    private final InpParser parser = new InpParser();
    private final NetworkAssembler assembler = new NetworkAssembler();
    private final GeoJsonCombiner geoJsonCombiner = new GeoJsonCombiner();
    private final NonVisualSectionCombiner sectionCombiner = new NonVisualSectionCombiner();
    private final OptionsCombiner optionsCombiner = new OptionsCombiner();

    // 역흐름(Compose → Write)
    private final InpComposer composer = new InpComposer();
    private final InpWriter writer = new InpWriter();

    @BeforeAll
    static void loadResources() throws IOException {
        responseJson = readResource("/sample/response.json");
        originalInp = readResource("/sample/network-sample.inp");
    }

    @Test
    @DisplayName("라운드트립: response.json → INP 쓰기 → 재파싱이 원본 INP 파싱과 의미적으로 동일하다")
    void roundTripMatchesOriginal() throws IOException {
        // 1) 상세조회 응답(data) 을 저장 요청으로 역직렬화 → 2) Compose+Write 로 INP 생성
        NetworkSaveRequest request = readSaveRequest(responseJson);
        byte[] writtenInp = writer.writeBytes(composer.compose(request));

        // 산출물을 doc/sample 에 남겨 육안 비교 가능하게 한다(검증에는 영향 없음)
        writeArtifact(writtenInp);

        // 3) 동일 파이프라인으로 양쪽을 파싱해 {layers, sections, options} 트리를 만든다
        JsonNode fromWritten = pipelineTree(writtenInp);
        JsonNode fromOriginal = pipelineTree(originalInp);

        // 4) 의미 동일성: 첫 불일치 지점을 함께 보고
        String diff = firstDiff(fromOriginal, fromWritten, "");
        assertThat(diff)
                .as("재작성 INP 의 파싱 결과가 원본과 달라진 지점: %s", diff)
                .isNull();
    }

    @Test
    @DisplayName("쓰기: 생성된 INP 는 CP949 로 디코딩되며 한글이 깨지지 않고 모든 표준 섹션을 포함한다")
    void writtenInpIsCp949AndComplete() {
        NetworkSaveRequest request = readSaveRequest(responseJson);
        String text = writer.write(composer.compose(request));

        // 모든 핵심 섹션 헤더가 존재한다
        assertThat(text)
                .contains("[TITLE]", "[JUNCTIONS]", "[RESERVOIRS]", "[TANKS]", "[PIPES]", "[PUMPS]", "[VALVES]",
                        "[STATUS]", "[CURVES]", "[RULES]", "[ENERGY]", "[REACTIONS]", "[TIMES]", "[REPORT]",
                        "[OPTIONS]", "[COORDINATES]", "[VERTICES]", "[LABELS]", "[BACKDROP]", "[END]");

        // CP949 로 인코딩했다가 다시 디코딩해도 한글이 보존된다(치환문자 없음)
        byte[] bytes = writer.writeBytes(composer.compose(request));
        String decoded = new String(bytes, java.nio.charset.Charset.forName("MS949"));
        assertThat(decoded.indexOf('�')).isEqualTo(-1);
        // 한글 펌프 곡선/라벨 등 한글 토큰이 살아 있다(첫 절점 ID "고산분기")
        assertThat(decoded).contains("고산분기");
    }

    @Test
    @DisplayName("객체 수 보존: 재파싱한 레이어 객체 수가 원본과 동일하다(절점 515/링크 531/라벨 87)")
    void preservesObjectCounts() {
        NetworkSaveRequest request = readSaveRequest(responseJson);
        byte[] writtenInp = writer.writeBytes(composer.compose(request));

        NetworkLayers layers = geoJsonCombiner.toLayers(assembler.assemble(parse(writtenInp)));
        assertThat(layers.nodeLayer().features()).hasSize(515);
        assertThat(layers.linkLayer().features()).hasSize(531);
        assertThat(layers.labelLayer().features()).hasSize(87);

        ParsedInp parsed = parse(writtenInp);
        // 정점(VERTICES) 총량까지 동일하게 복원된다
        assertThat(parsed.rows(com.mindone.editor.inp.network.parser.InpSectionType.VERTICES)).hasSize(10366);
        assertThat(parsed.ruleBlocks()).hasSize(8);
    }

    @Test
    @DisplayName("저장 계약: 조회가 만든 sections(CONTROLS{simple,rule}/OPTIONS)를 저장이 그대로 되읽어 복원한다")
    void savePreservesControlsAndOptionsFromForwardSections() throws IOException {
        // 픽스처가 아니라 <b>포워드 파이프라인이 실제로 만든 sections</b> 를 저장 요청으로 왕복시킨다.
        // (JSON 직렬화/역직렬화를 거쳐 컨트롤러가 받는 경로와 동일한 형태로 검증)
        ParsedInp origin = parse(originalInp);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("meta", null);
        view.put("layers", geoJsonCombiner.toLayers(assembler.assemble(origin)));
        view.put("sections", forwardSections(origin));

        NetworkSaveRequest request = MAPPER.treeToValue(MAPPER.valueToTree(view), NetworkSaveRequest.class);
        ParsedInp reparsed = parse(writer.writeBytes(composer.compose(request)));

        // CONTROLS.rule → [RULES] 섹션으로 복원(원본 규칙 8개)
        assertThat(reparsed.ruleBlocks()).as("규칙 제어 보존").hasSize(8);
        // CONTROLS.simple → [CONTROLS] 섹션으로 복원(원본 단순 제어 수와 동일)
        assertThat(reparsed.rows(InpSectionType.CONTROLS))
                .as("단순 제어 보존")
                .hasSameSizeAs(origin.rows(InpSectionType.CONTROLS));

        // sections.OPTIONS → 설정 4개 섹션으로 복원되어 조회 시 동일한 5범주가 다시 나온다
        assertThat(json(optionsCombiner.combine(reparsed)))
                .as("설정(OPTIONS/TIMES/REACTIONS/ENERGY) 보존")
                .isEqualTo(json(optionsCombiner.combine(origin)));

        // 조회를 다시 돌려도 CONTROLS 는 {simple, rule} 한 묶음으로 나오고 별도 RULES 키는 없다
        Map<String, Object> resections = forwardSections(reparsed);
        assertThat(resections).doesNotContainKey("RULES");
        assertThat(json(resections.get("CONTROLS")))
                .as("제어(CONTROLS{simple,rule}) 보존")
                .isEqualTo(json(forwardSections(origin).get("CONTROLS")));
    }

    @Test
    @DisplayName("저장 계약: 값이 null 인 설정 키는 INP 줄을 만들지 않는다(전체 스키마 고정의 부작용 차단)")
    void nullOptionValuesDoNotEmitInpLines() {
        // 조회는 전체 스키마를 노출하므로 원본에 없는 항목이 null 로 들어온다.
        // 이 null 이 그대로 INP 로 나가면 "DURATION" 처럼 값 없는 줄이 생겨 파일이 깨진다.
        ParsedInp origin = parse(originalInp);
        Map<String, Object> sections = forwardSections(origin);
        Map<String, Object> options = asMap(sections.get("OPTIONS"));

        // 샘플에 없는 항목들이 실제로 null 로 노출되고 있음을 먼저 확인(테스트 전제)
        assertThat(asMap(options.get("energy")).get("pricePattern")).isNull();
        assertThat(asMap(options.get("times")).get("qualityTimeStep")).isNotNull();

        String text = writer.write(composer.compose(new NetworkSaveRequest(null, null, sections)));

        // null 항목의 INP 키워드가 등장하지 않는다
        assertThat(text).doesNotContain("GLOBAL PATTERN");
        // 값 없이 키워드만 있는 줄(= 깨진 줄)이 없다: 모든 설정 줄은 토큰이 2개 이상이다
        for (String section : List.of("[OPTIONS]", "[TIMES]", "[ENERGY]", "[REACTIONS]")) {
            for (String line : sectionLines(text, section)) {
                assertThat(line.trim().split("\\s+"))
                        .as("%s 의 줄 '%s' 은 키워드와 값을 모두 가져야 한다", section, line)
                        .hasSizeGreaterThanOrEqualTo(2);
            }
        }

        // 재파싱해도 원본과 동일한 설정이 나온다(누락/추가 없음)
        assertThat(json(optionsCombiner.combine(parse(text.getBytes(java.nio.charset.Charset.forName("MS949"))))))
                .isEqualTo(json(optionsCombiner.combine(origin)));
    }

    @Test
    @DisplayName("픽스처 최신성: response.json 이 현재 상세조회 응답 구조와 일치한다(스키마 드리프트 감지)")
    void responseFixtureMatchesCurrentPipelineOutput() throws IOException {
        // response.json 은 network-sample.inp 의 상세조회 응답을 떠 놓은 것이다. 응답 구조를 바꾸고 픽스처를
        // 갱신하지 않으면 라운드트립 테스트가 옛 구조를 검증하게 되므로, 여기서 드리프트를 잡는다.
        JsonNode fixture = MAPPER.readTree(responseJson).get("data");
        ParsedInp origin = parse(originalInp);

        assertThat(json(forwardSections(origin)))
                .as("sections(설정 OPTIONS 포함)가 픽스처와 달라졌다면 response.json 을 재생성해야 한다")
                .isEqualTo(fixture.get("sections").toString());
        assertThat(MAPPER.valueToTree(geoJsonCombiner.toLayers(assembler.assemble(origin))).toString())
                .as("layers 가 픽스처와 달라졌다면 response.json 을 재생성해야 한다")
                .isEqualTo(fixture.get("layers").toString());
    }

    @Test
    @DisplayName("저장 계약: 요소별 반응계수는 options 가 아니라 객체 properties 로만 왕복한다")
    void elementReactionCoefficientsRoundTripViaObjectProperties() {
        ParsedInp origin = parse(originalInp);
        Map<String, Object> options = asMap(forwardSections(origin).get("OPTIONS"));

        // options.reactions 에는 전역 설정만 있고 요소별 계수는 없다(EPANET Reactions 패널과 동일)
        assertThat(asMap(options.get("reactions"))).doesNotContainKey("elementCoeffs");

        // 그럼에도 원본의 요소별 계수 행은 저장 후에도 그대로다 — 객체 properties 가 단일 출처이기 때문
        byte[] written = writer.writeBytes(composer.compose(readSaveRequest(responseJson)));
        assertThat(elementCoeffRows(parse(written)))
                .as("BULK/WALL/TANK 요소별 계수 행이 보존되어야 한다")
                .isEqualTo(elementCoeffRows(origin));
    }

    // ===== 헬퍼 =====

    /** [REACTIONS] 의 요소별 계수 행(BULK/WALL/TANK id coef)만 문자열로 뽑는다. */
    private List<String> elementCoeffRows(ParsedInp parsed) {
        List<String> out = new ArrayList<>();
        for (var row : parsed.rows(InpSectionType.REACTIONS)) {
            String keyword = row.token(0) == null ? "" : row.token(0).toUpperCase(java.util.Locale.ROOT);
            if (keyword.equals("BULK") || keyword.equals("WALL") || keyword.equals("TANK")) {
                out.add(keyword + " " + row.token(1) + " " + row.token(2));
            }
        }
        return out;
    }

    /** 지정 섹션의 데이터 줄만 뽑는다(주석/빈 줄 제외). */
    private List<String> sectionLines(String text, String header) {
        int start = text.indexOf(header);
        assertThat(start).as("%s 섹션 존재", header).isNotNegative();
        int end = text.indexOf('[', start + header.length());
        String body = end < 0 ? text.substring(start) : text.substring(start, end);
        List<String> lines = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith(";") && !t.startsWith("[")) {
                lines.add(t);
            }
        }
        return lines;
    }

    /** {@code sections.OPTIONS} 하위 범주를 맵으로 꺼낸다. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** response.json 의 {@code data} 노드를 저장 요청 DTO 로 역직렬화한다. */
    private NetworkSaveRequest readSaveRequest(byte[] json) {
        try {
            JsonNode data = MAPPER.readTree(json).get("data");
            return MAPPER.treeToValue(data, NetworkSaveRequest.class);
        } catch (IOException e) {
            throw new IllegalStateException("response.json 역직렬화 실패", e);
        }
    }

    /** INP 바이트를 포워드 파이프라인으로 파싱해 {layers, sections} JSON 트리로 만든다(설정은 sections.OPTIONS). */
    private JsonNode pipelineTree(byte[] inp) {
        ParsedInp parsed = parse(inp);
        NetworkModel model = assembler.assemble(parsed);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("layers", geoJsonCombiner.toLayers(model));
        view.put("sections", forwardSections(parsed));
        return MAPPER.valueToTree(view);
    }

    /** 비교용 JSON 문자열(JsonNode 직접 비교는 AssertJ 오버로드가 모호해 문자열로 맞춘다). */
    private String json(Object value) {
        return MAPPER.valueToTree(value).toString();
    }

    /** 상세조회 응답의 {@code sections} 를 서비스와 동일하게 조립한다(비가시 섹션 + 설정 OPTIONS). */
    private Map<String, Object> forwardSections(ParsedInp parsed) {
        Map<String, Object> sections = sectionCombiner.combine(parsed);
        sections.put("OPTIONS", optionsCombiner.combine(parsed));
        return sections;
    }

    private ParsedInp parse(byte[] inp) {
        return parser.parse(reader.read(inp).lines());
    }

    /** 두 JSON 트리의 첫 불일치 지점을 JSON 포인터로 반환한다(같으면 {@code null}). */
    private String firstDiff(JsonNode expected, JsonNode actual, String path) {
        if (expected == null || actual == null) {
            return expected == actual ? null : path + " (one side missing)";
        }
        if (expected.isObject() && actual.isObject()) {
            // 키 집합 비교
            for (var it = expected.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!actual.has(name)) {
                    return path + "/" + name + " (missing in written)";
                }
                String d = firstDiff(expected.get(name), actual.get(name), path + "/" + name);
                if (d != null) {
                    return d;
                }
            }
            for (var it = actual.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!expected.has(name)) {
                    return path + "/" + name + " (unexpected in written)";
                }
            }
            return null;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return path + " (array size " + expected.size() + " vs " + actual.size() + ")";
            }
            for (int i = 0; i < expected.size(); i++) {
                String d = firstDiff(expected.get(i), actual.get(i), path + "/" + i);
                if (d != null) {
                    return d;
                }
            }
            return null;
        }
        if (expected.isNumber() && actual.isNumber()) {
            // 숫자는 수치로 비교(200 vs 200.0 동치 처리)
            return expected.asDouble() == actual.asDouble() ? null
                    : path + " (number " + expected.asText() + " vs " + actual.asText() + ")";
        }
        if (!expected.equals(actual)) {
            return path + " (" + expected.asText() + " vs " + actual.asText() + ")";
        }
        return null;
    }

    /** 직렬화 산출물을 doc/sample 에 기록한다(있으면 덮어씀). */
    private void writeArtifact(byte[] inp) throws IOException {
        Path out = Path.of("doc", "sample", "composed_output.inp");
        Files.createDirectories(out.getParent());
        Files.write(out, inp);
        assertThat(Files.size(out)).isPositive();
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = InpComposeWriteTest.class.getResourceAsStream(name)) {
            assertThat(in).as("테스트 리소스 %s 존재", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
