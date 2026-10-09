package com.mindone.editor.inp.network;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.compose.InpComposer;
import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.geojson.GeoJsonFeature;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.writer.InpWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "없던 값들 샘플 보강" 라운드트립 검증 — {@code response.json}(원본은 탱크·태그·다중수요·이미터·수질·소스·
 * 혼합·패턴·컨트롤 등이 모두 비어 있음)에 <b>새 객체와 속성을 채워</b> 저장 요청을 만들고, INP 로 작성한 뒤
 * 같은 포워드 파이프라인으로 재파싱했을 때 <b>보강한 의미가 무손실로 복원</b>됨을 확인한다.
 *
 * <p>{@link InpComposeWriteTest}(원본과 의미 동일)와 달리, 이 테스트는 프론트가 신규/편집한 데이터가
 * Composer→Writer→재파싱 왕복에서 보존되는지를 본다(편집 시나리오의 회귀 가드).</p>
 *
 * <p>산출물 {@code doc/sample/composed_enriched.inp} 를 남겨 육안 확인이 가능하게 한다.</p>
 */
class InpComposeEnrichedRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Charset MS949 = Charset.forName("MS949");

    // 포워드 파이프라인(재파싱)
    private final InpFileReader reader = new InpFileReader();
    private final InpParser parser = new InpParser();
    private final NetworkAssembler assembler = new NetworkAssembler();
    private final GeoJsonCombiner geoJsonCombiner = new GeoJsonCombiner();

    // 역흐름(Compose → Write)
    private final InpComposer composer = new InpComposer();
    private final InpWriter writer = new InpWriter();

    @Test
    @DisplayName("보강 라운드트립: 신규 탱크/태그/다중수요/이미터/수질/소스/혼합/패턴/컨트롤이 INP 왕복에서 보존된다")
    void enrichedRoundTripPreservesAddedData() throws IOException {
        // ── 0) 원본 응답 로드 ───────────────────────────────────────────────
        JsonNode root = MAPPER.readTree(readResource("/sample/response.json"));
        ObjectNode data = (ObjectNode) root.get("data");
        ArrayNode nodeFeatures = (ArrayNode) data.path("layers").path("nodeLayer").path("features");
        ArrayNode linkFeatures = (ArrayNode) data.path("layers").path("linkLayer").path("features");
        ObjectNode sections = (ObjectNode) data.get("sections");

        int originalNodeCount = nodeFeatures.size();
        int originalLinkCount = linkFeatures.size();
        assertThat(tankCount(data)).as("원본에는 탱크가 없다").isZero();

        // ── 1) 기존 절점 보강(태그/다중수요/이미터/초기수질/수질공급원) ──────────
        JsonNode junction = firstByType(nodeFeatures, "junction");
        String junctionId = junction.path("properties").path("id").asText();
        double jx = junction.path("geometry").path("coordinates").get(0).asDouble();
        double jy = junction.path("geometry").path("coordinates").get(1).asDouble();
        ObjectNode jProps = (ObjectNode) junction.get("properties");
        jProps.put("description", "고산 분기점(샘플 보강)");
        jProps.put("tag", "Zone_A");
        jProps.put("emitterCoefficient", 1.5);
        jProps.put("initialQuality", 0.5);
        jProps.set("sourceQuality", MAPPER.readTree(
                "{\"sourceType\":\"CONCEN\",\"sourceQuality\":1.2,\"qualityPattern\":\"SRC_PAT\"}"));
        jProps.set("demandCategories", MAPPER.readTree("""
                [ {"baseDemand":100,"timePattern":"DAY_PAT","category":"Domestic"},
                  {"baseDemand":25, "timePattern":"DAY_PAT","category":"School"} ]"""));

        // ── 2) 기존 저수지 보강(수두 변동 패턴 + 수질공급원) ────────────────────
        JsonNode reservoir = firstByType(nodeFeatures, "reservoir");
        String reservoirId = reservoir.path("properties").path("id").asText();
        ObjectNode rProps = (ObjectNode) reservoir.get("properties");
        rProps.put("headPattern", "HEAD_PAT");
        rProps.set("sourceQuality", MAPPER.readTree(
                "{\"sourceType\":\"SETPOINT\",\"sourceQuality\":0.8,\"qualityPattern\":null}"));

        // ── 3) 신규 탱크 노드 추가(혼합 모델/반응계수/체적곡선/초기수질 포함) ─────
        double tankX = jx + 250.0;
        double tankY = jy + 250.0;
        nodeFeatures.add(MAPPER.readTree("""
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [%s, %s] },
                  "properties": {
                    "id": "샘플배수지",
                    "objectType": "tank",
                    "layer": "tanks",
                    "description": "샘플 보강 배수지",
                    "tag": "Zone_T",
                    "elevation": 100.0,
                    "initialLevel": 15.0,
                    "minimumLevel": 5.0,
                    "maximumLevel": 25.0,
                    "diameter": 120.0,
                    "minimumVolume": 0,
                    "volumeCurve": "TANK_VOL",
                    "canOverflow": "NO",
                    "mixingModel": "2COMP",
                    "mixingFraction": 0.2,
                    "reactionCoefficient": -0.5,
                    "initialQuality": 0.3,
                    "sourceQuality": null
                  }
                }""".formatted(tankX, tankY)));

        // ── 4) 신규 관로 추가(절점→신규 탱크 연결, 태그/벌크·벽면 반응계수 포함) ──
        linkFeatures.add(MAPPER.readTree("""
                {
                  "type": "Feature",
                  "geometry": { "type": "LineString", "coordinates": [[%s,%s],[%s,%s]] },
                  "properties": {
                    "id": "샘플관로",
                    "objectType": "pipe",
                    "layer": "pipes",
                    "startNode": "%s",
                    "endNode": "샘플배수지",
                    "description": "샘플 보강 관로",
                    "tag": "PVC-2026",
                    "length": 150.0,
                    "diameter": 200.0,
                    "roughness": 110.0,
                    "lossCoefficient": 0.0,
                    "initialStatus": "Open",
                    "bulkCoefficient": -0.8,
                    "wallCoefficient": -1.0
                  }
                }""".formatted(jx, jy, tankX, tankY, junctionId)));

        // ── 5) 비가시 섹션 보강(TITLE/PATTERNS/CURVES/CONTROLS) ─────────────────
        ArrayNode title = MAPPER.createArrayNode();
        title.add("전북 광역상수도 관망 모델");
        title.add("2026년 개정본 - 샘플 보강 라운드트립");
        sections.set("TITLE", title);

        sections.set("PATTERNS", MAPPER.readTree("""
                [ {"id":"DAY_PAT","description":"주간 수요 패턴","multipliers":[1.1,1.4,0.9,0.7,0.6,0.5,0.8,1.0]},
                  {"id":"HEAD_PAT","description":"저수지 수두 변동","multipliers":[1.0,1.02,1.05,1.03,1.0,0.98]},
                  {"id":"SRC_PAT","description":null,"multipliers":[1,1,1,1]} ]"""));

        ((ArrayNode) sections.get("CURVES")).add(MAPPER.readTree("""
                {"id":"TANK_VOL","curveType":"VOLUME","description":"샘플배수지 체적곡선",
                 "xyData":[{"x":0,"y":0},{"x":10,"y":1000},{"x":25,"y":3000}]}"""));

        // 제어는 CONTROLS 묶음의 simple(단순 제어문)만 교체한다 — rule(규칙 제어)은 원본 그대로 유지
        ArrayNode simpleControls = MAPPER.createArrayNode();
        simpleControls.add("LINK 샘플관로 OPEN IF NODE 샘플배수지 BELOW 10");
        simpleControls.add("LINK 샘플관로 CLOSED IF NODE 샘플배수지 ABOVE 24");
        ((ObjectNode) sections.get("CONTROLS")).set("simple", simpleControls);

        // ── 6) 역변환 → INP 작성 → 산출물 기록 ─────────────────────────────────
        NetworkSaveRequest request = MAPPER.treeToValue(data, NetworkSaveRequest.class);
        byte[] writtenInp = writer.writeBytes(composer.compose(request));
        Path out = Path.of("doc", "sample", "composed_enriched.inp");
        Files.createDirectories(out.getParent());
        Files.write(out, writtenInp);

        // ── 7-A) 섹션 단위 검증(재파싱한 토큰 행) ───────────────────────────────
        ParsedInp reparsed = parse(writtenInp);
        assertThat(reparsed.rows(InpSectionType.TITLE)).as("TITLE 2줄").hasSize(2);
        assertThat(reparsed.rows(InpSectionType.TANKS)).as("신규 탱크 1").hasSize(1);
        assertThat(reparsed.rows(InpSectionType.PATTERNS)).as("패턴 라인 보존").isNotEmpty();
        assertThat(reparsed.rows(InpSectionType.CONTROLS)).as("컨트롤 2줄").hasSize(2);
        assertThat(reparsed.rows(InpSectionType.TAGS)).as("태그 3건(절점/탱크/관로)").hasSize(3);
        assertThat(reparsed.rows(InpSectionType.DEMANDS)).as("다중수요 2범주").hasSize(2);
        assertThat(reparsed.rows(InpSectionType.EMITTERS)).as("이미터 1건").hasSize(1);
        assertThat(reparsed.rows(InpSectionType.QUALITY)).as("초기수질 2건(절점/탱크)").hasSize(2);
        assertThat(reparsed.rows(InpSectionType.SOURCES)).as("수질공급원 2건(절점/저수지)").hasSize(2);
        assertThat(reparsed.rows(InpSectionType.MIXING)).as("혼합모델 1건(탱크)").hasSize(1);

        // ── 7-B) 객체 단위 검증(재파싱→결합한 레이어 속성) ──────────────────────
        NetworkLayers layers = geoJsonCombiner.toLayers(assembler.assemble(reparsed));
        assertThat(layers.nodeLayer().features()).as("노드 +1").hasSize(originalNodeCount + 1);
        assertThat(layers.linkLayer().features()).as("링크 +1").hasSize(originalLinkCount + 1);

        Map<String, Object> tank = findProps(layers.nodeLayer().features(), "샘플배수지");
        assertThat(tank.get("objectType")).isEqualTo("tank");
        assertThat(num(tank.get("elevation"))).isEqualTo(100.0);
        assertThat(num(tank.get("maximumLevel"))).isEqualTo(25.0);
        assertThat(tank.get("volumeCurve")).isEqualTo("TANK_VOL");
        assertThat(tank.get("canOverflow")).isEqualTo("NO");
        assertThat(tank.get("mixingModel")).isEqualTo("2COMP");
        assertThat(num(tank.get("mixingFraction"))).isEqualTo(0.2);
        assertThat(num(tank.get("reactionCoefficient"))).isEqualTo(-0.5);
        assertThat(num(tank.get("initialQuality"))).isEqualTo(0.3);
        assertThat(tank.get("tag")).isEqualTo("Zone_T");

        Map<String, Object> jct = findProps(layers.nodeLayer().features(), junctionId);
        assertThat(jct.get("tag")).isEqualTo("Zone_A");
        assertThat(num(jct.get("emitterCoefficient"))).isEqualTo(1.5);
        assertThat(num(jct.get("initialQuality"))).isEqualTo(0.5);
        assertThat(jct.get("sourceQuality")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) jct.get("sourceQuality")).get("sourceType")).isEqualTo("CONCEN");
        assertThat((List<?>) jct.get("demandCategories")).as("다중수요 2범주 복원").hasSize(2);

        Map<String, Object> rsv = findProps(layers.nodeLayer().features(), reservoirId);
        assertThat(rsv.get("headPattern")).isEqualTo("HEAD_PAT");
        assertThat(((Map<?, ?>) rsv.get("sourceQuality")).get("sourceType")).isEqualTo("SETPOINT");

        Map<String, Object> pipe = findProps(layers.linkLayer().features(), "샘플관로");
        assertThat(pipe.get("startNode")).isEqualTo(junctionId);
        assertThat(pipe.get("endNode")).isEqualTo("샘플배수지");
        assertThat(pipe.get("tag")).isEqualTo("PVC-2026");
        assertThat(num(pipe.get("bulkCoefficient"))).isEqualTo(-0.8);
        assertThat(num(pipe.get("wallCoefficient"))).isEqualTo(-1.0);

        // ── 7-C) CP949 한글 보존 ───────────────────────────────────────────────
        String decoded = new String(writtenInp, MS949);
        assertThat(decoded.indexOf('�')).as("치환문자 없음").isEqualTo(-1);
        // 정확한 TITLE 문자열까지 포함 → CP949 안전 문자만 써서 치환('?', 0x3F) 없이 왕복됨을 보장
        assertThat(decoded).contains("샘플배수지", "DAY_PAT", "TANK_VOL",
                "전북 광역상수도 관망 모델", "2026년 개정본 - 샘플 보강 라운드트립");
    }

    // ===== 헬퍼 =====

    /** features 배열에서 주어진 objectType 의 첫 Feature 를 찾는다. */
    private JsonNode firstByType(ArrayNode features, String objectType) {
        for (JsonNode f : features) {
            if (objectType.equals(f.path("properties").path("objectType").asText())) {
                return f;
            }
        }
        throw new IllegalStateException("objectType=" + objectType + " 인 Feature 가 없다");
    }

    /** data 트리에서 탱크 노드 수를 센다. */
    private int tankCount(JsonNode data) {
        int n = 0;
        for (JsonNode f : data.path("layers").path("nodeLayer").path("features")) {
            if ("tank".equals(f.path("properties").path("objectType").asText())) {
                n++;
            }
        }
        return n;
    }

    /** 재파싱한 Feature 목록에서 id 로 properties 맵을 찾는다. */
    private Map<String, Object> findProps(List<GeoJsonFeature> features, String id) {
        return features.stream()
                .filter(f -> id.equals(f.properties().get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("id=" + id + " Feature 없음"))
                .properties();
    }

    private double num(Object o) {
        return ((Number) o).doubleValue();
    }

    private ParsedInp parse(byte[] inp) {
        return parser.parse(reader.read(inp).lines());
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = InpComposeEnrichedRoundTripTest.class.getResourceAsStream(name)) {
            assertThat(in).as("테스트 리소스 %s 존재", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
