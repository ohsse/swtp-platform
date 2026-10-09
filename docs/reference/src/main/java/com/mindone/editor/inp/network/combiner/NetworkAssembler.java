package com.mindone.editor.inp.network.combiner;

import com.mindone.editor.inp.network.model.Coord;
import com.mindone.editor.inp.network.model.LinkType;
import com.mindone.editor.inp.network.model.MapLabel;
import com.mindone.editor.inp.network.model.NetworkBounds;
import com.mindone.editor.inp.network.model.NetworkLink;
import com.mindone.editor.inp.network.model.NetworkModel;
import com.mindone.editor.inp.network.model.NetworkNode;
import com.mindone.editor.inp.network.model.NetworkOptions;
import com.mindone.editor.inp.network.model.NodeType;
import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.parser.SectionRow;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link ParsedInp}(섹션 구조) 를 {@link NetworkModel}(타입드 가시 객체 + 좌표 결합) 으로 조립한다(Combine 1단계).
 *
 * <p>노드/링크를 만들고 {@code [COORDINATES]}/{@code [VERTICES]} 와 결합해 점/선 형상을 완성한다.
 * 또한 객체별 속성이 흩어진 섹션({@code [TAGS]}/{@code [DEMANDS]}/{@code [EMITTERS]}/{@code [QUALITY]}/
 * {@code [SOURCES]}/{@code [MIXING]}/{@code [REACTIONS]}/{@code [ENERGY]})을 ID 로 join 해 해당 노드/링크의
 * {@code properties} 에 합친다 — EPANET 속성편집기(매뉴얼 6.4)가 여러 섹션의 값을 모아 한 객체로 보여주는
 * 통합 뷰와 동일하다. 객체의 인라인 주석은 {@code description} 으로 노출한다.
 * 좌표 누락·미참조 ID 등 결합 과정의 이상은 예외 대신 경고로 모아 응답 메타에 노출한다.</p>
 */
@Slf4j
@Component
public class NetworkAssembler {

    /**
     * 파싱 결과를 네트워크 모델로 조립한다.
     *
     * @param parsed 전 섹션 파싱 결과
     * @return 좌표가 결합된 네트워크 모델
     */
    public NetworkModel assemble(ParsedInp parsed) {
        List<String> warnings = new ArrayList<>();

        // 1. 노드/링크 생성 (ID 인덱스도 함께 구성)
        List<NetworkNode> nodes = new ArrayList<>();
        Map<String, NetworkNode> nodeById = new LinkedHashMap<>();
        buildJunctions(parsed, nodes, nodeById);
        buildReservoirs(parsed, nodes, nodeById);
        buildTanks(parsed, nodes, nodeById);

        List<NetworkLink> links = new ArrayList<>();
        Map<String, NetworkLink> linkById = new LinkedHashMap<>();
        buildPipes(parsed, links, linkById);
        buildPumps(parsed, links, linkById);
        buildValves(parsed, links, linkById);

        // 2. 좌표/정점 결합
        joinCoordinates(parsed, nodeById, warnings);
        joinVertices(parsed, linkById, warnings);

        // 3. STATUS 오버라이드(링크 상태)
        applyStatus(parsed, linkById);

        // 4. 객체별 속성 섹션 병합(TAGS/DEMANDS/EMITTERS/QUALITY/SOURCES/MIXING/REACTIONS/ENERGY)
        mergeAttributeSections(parsed, nodeById, linkById, warnings);

        // 5. 라벨
        List<MapLabel> labels = buildLabels(parsed);

        // 6. 경고 집계(좌표 누락 노드/링크)
        collectGeometryWarnings(nodes, links, nodeById, warnings);

        // 7. 옵션 / 경계
        NetworkOptions options = NetworkOptions.from(parsed.rows(InpSectionType.OPTIONS));
        NetworkBounds bounds = computeBounds(nodes, links, labels);
        NetworkBounds backdropBounds = parseBackdrop(parsed);

        return NetworkModel.builder()
                .nodes(nodes)
                .links(links)
                .labels(labels)
                .options(options)
                .bounds(bounds)
                .backdropBounds(backdropBounds)
                .warnings(warnings)
                .parsed(parsed)
                .build();
    }

    // ===== 노드 =====

    private void buildJunctions(ParsedInp parsed, List<NetworkNode> nodes, Map<String, NetworkNode> index) {
        for (SectionRow row : parsed.rows(InpSectionType.JUNCTIONS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 절점 속성편집기(Table 6.1) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Elevation/
            // Base Demand/Demand Pattern/Demand Categories/Emitter Coefficient/Initial Quality/Source Quality.
            // 카테고리·이미터·수질·소스는 병합 단계에서 채워진다.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            putNum(props, "elevation", row.token(1));        // Elevation
            putNum(props, "baseDemand", row.token(2));       // Base Demand
            putStr(props, "demandPattern", row.token(3));    // Demand Pattern
            props.put("demandCategories", null);            // Demand Categories [DEMANDS]
            props.put("emitterCoefficient", null);          // Emitter Coefficient [EMITTERS]
            props.put("initialQuality", null);              // Initial Quality [QUALITY]
            props.put("sourceQuality", null);               // Source Quality [SOURCES]
            register(new NetworkNode(id, NodeType.JUNCTION, props), nodes, index);
        }
    }

    private void buildReservoirs(ParsedInp parsed, List<NetworkNode> nodes, Map<String, NetworkNode> index) {
        for (SectionRow row : parsed.rows(InpSectionType.RESERVOIRS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 저수지 속성편집기(Table 6.2) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Total Head/
            // Head Pattern/Initial Quality/Source Quality.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            putNum(props, "totalHead", row.token(1));        // Total Head
            putStr(props, "headPattern", row.token(2));      // Head Pattern
            props.put("initialQuality", null);              // Initial Quality [QUALITY]
            props.put("sourceQuality", null);               // Source Quality [SOURCES]
            register(new NetworkNode(id, NodeType.RESERVOIR, props), nodes, index);
        }
    }

    private void buildTanks(ParsedInp parsed, List<NetworkNode> nodes, Map<String, NetworkNode> index) {
        for (SectionRow row : parsed.rows(InpSectionType.TANKS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 탱크 속성편집기(Table 6.3) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Elevation/
            // Initial·Minimum·Maximum Level/Diameter/Minimum Volume/Volume Curve/Can Overflow/Mixing Model·Fraction/
            // Reaction Coefficient/Initial Quality/Source Quality. 혼합·반응·수질·소스는 병합 단계에서 채워진다.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            putNum(props, "elevation", row.token(1));        // Elevation
            putNum(props, "initialLevel", row.token(2));     // Initial Level
            putNum(props, "minimumLevel", row.token(3));     // Minimum Level
            putNum(props, "maximumLevel", row.token(4));     // Maximum Level
            putNum(props, "diameter", row.token(5));         // Diameter
            putNum(props, "minimumVolume", row.token(6));    // Minimum Volume
            putStr(props, "volumeCurve", row.token(7));      // Volume Curve
            putStr(props, "canOverflow", EnumNormalizer.upper(row.token(8))); // Can Overflow(YES/NO enum → 대문자 통일)
            props.put("mixingModel", null);                 // Mixing Model [MIXING]
            props.put("mixingFraction", null);              // Mixing Fraction [MIXING]
            props.put("reactionCoefficient", null);         // Reaction Coefficient [REACTIONS] TANK
            props.put("initialQuality", null);              // Initial Quality [QUALITY]
            props.put("sourceQuality", null);               // Source Quality [SOURCES]
            register(new NetworkNode(id, NodeType.TANK, props), nodes, index);
        }
    }

    // ===== 링크 =====

    private void buildPipes(ParsedInp parsed, List<NetworkLink> links, Map<String, NetworkLink> index) {
        for (SectionRow row : parsed.rows(InpSectionType.PIPES)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 관로 속성편집기(Table 6.4) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Length/Diameter/
            // Roughness/Loss Coefficient/Initial Status/Bulk·Wall Coefficient. 반응계수는 병합 단계에서 채워진다.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            putNum(props, "length", row.token(3));           // Length
            putNum(props, "diameter", row.token(4));         // Diameter
            putNum(props, "roughness", row.token(5));        // Roughness
            putNum(props, "lossCoefficient", row.token(6));  // Loss Coefficient
            putStr(props, "initialStatus", EnumNormalizer.upper(row.token(7))); // Initial Status(OPEN/CLOSED/CV enum → 대문자 통일)
            props.put("bulkCoefficient", null);             // Bulk Coefficient [REACTIONS] BULK
            props.put("wallCoefficient", null);             // Wall Coefficient [REACTIONS] WALL
            register(new NetworkLink(id, LinkType.PIPE, row.token(1), row.token(2), props), links, index);
        }
    }

    private void buildPumps(ParsedInp parsed, List<NetworkLink> links, Map<String, NetworkLink> index) {
        for (SectionRow row : parsed.rows(InpSectionType.PUMPS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 펌프 속성편집기(Table 6.5) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Pump Curve/Power/
            // Speed/Pattern/Initial Status/Efficiency Curve/Energy Price/Price Pattern. 먼저 전체 키를 null 로
            // 깔고 [PUMPS] 키워드 쌍으로 덮어쓴다. 상태/효율곡선/단가/단가패턴은 STATUS·ENERGY 병합 단계에서 채워진다.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            props.put("pumpCurve", null);                   // Pump Curve
            props.put("power", null);                       // Power
            props.put("speed", null);                       // Speed
            props.put("pattern", null);                     // Pattern(속도 변동)
            props.put("initialStatus", null);               // Initial Status [STATUS]
            props.put("efficiencyCurve", null);             // Efficiency Curve [ENERGY]
            props.put("energyPrice", null);                 // Energy Price [ENERGY]
            props.put("pricePattern", null);                // Price Pattern [ENERGY]
            // Node1, Node2 이후는 keyword/value 쌍: HEAD curve | POWER x | SPEED x | PATTERN id
            for (int i = 3; i + 1 < row.size(); i += 2) {
                String key = row.token(i);
                String val = row.token(i + 1);
                if (key == null) {
                    continue;
                }
                switch (key.toUpperCase(Locale.ROOT)) {
                    case "HEAD" -> putStr(props, "pumpCurve", val);   // 펌프 특성곡선 ID(문자열)
                    case "POWER" -> putNum(props, "power", val);
                    case "SPEED" -> putNum(props, "speed", val);
                    case "PATTERN" -> putStr(props, "pattern", val);  // 속도 변동 패턴 ID(문자열)
                    default -> putNum(props, key.toLowerCase(Locale.ROOT), val);
                }
            }
            register(new NetworkLink(id, LinkType.PUMP, row.token(1), row.token(2), props), links, index);
        }
    }

    private void buildValves(ParsedInp parsed, List<NetworkLink> links, Map<String, NetworkLink> index) {
        for (SectionRow row : parsed.rows(InpSectionType.VALVES)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            // 6.4 밸브 속성편집기(Table 6.6) 라벨과 1:1 키(없는 값은 null) — Description/Tag/Diameter/Type/
            // Setting/Loss Coefficient/Fixed Status. 고정상태는 STATUS 병합 단계에서 채워진다.
            Map<String, Object> props = new LinkedHashMap<>();
            putDesc(props, row);                            // Description
            props.put("tag", null);                         // Tag [TAGS]
            putNum(props, "diameter", row.token(3));         // Diameter
            putStr(props, "type", EnumNormalizer.upper(row.token(4))); // Type(PRV/PSV/PBV/FCV/TCV/GPV enum → 대문자 통일)
            putNum(props, "setting", row.token(5));          // Setting
            putNum(props, "lossCoefficient", row.token(6));  // Loss Coefficient
            props.put("fixedStatus", null);                 // Fixed Status [STATUS]
            register(new NetworkLink(id, LinkType.VALVE, row.token(1), row.token(2), props), links, index);
        }
    }

    // ===== 좌표/정점 결합 =====

    private void joinCoordinates(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.COORDINATES)) {
            String nodeId = row.id();
            Double x = parseDouble(row.token(1));
            Double y = parseDouble(row.token(2));
            if (nodeId == null || x == null || y == null) {
                continue;
            }
            NetworkNode node = nodeById.get(nodeId);
            if (node == null) {
                orphan++;
                continue;
            }
            node.setCoord(new Coord(x, y));
        }
        if (orphan > 0) {
            warnings.add("[COORDINATES] 존재하지 않는 노드를 참조하는 좌표 " + orphan + "건");
        }
    }

    private void joinVertices(ParsedInp parsed, Map<String, NetworkLink> linkById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.VERTICES)) {
            String linkId = row.id();
            Double x = parseDouble(row.token(1));
            Double y = parseDouble(row.token(2));
            if (linkId == null || x == null || y == null) {
                continue;
            }
            NetworkLink link = linkById.get(linkId);
            if (link == null) {
                orphan++;
                continue;
            }
            link.addVertex(new Coord(x, y));
        }
        if (orphan > 0) {
            warnings.add("[VERTICES] 존재하지 않는 링크를 참조하는 정점 " + orphan + "건");
        }
    }

    private void applyStatus(ParsedInp parsed, Map<String, NetworkLink> linkById) {
        for (SectionRow row : parsed.rows(InpSectionType.STATUS)) {
            String linkId = row.id();
            String status = row.token(1);
            if (linkId == null || status == null) {
                continue;
            }
            NetworkLink link = linkById.get(linkId);
            if (link != null) {
                // 6.4 라벨: 관로/펌프는 Initial Status, 밸브는 Fixed Status
                String key = link.getType() == LinkType.VALVE ? "fixedStatus" : "initialStatus";
                link.getProperties().put(key, EnumNormalizer.upper(status)); // OPEN/CLOSED enum → 대문자 통일
            }
        }
    }

    // ===== 객체별 속성 섹션 병합 (EPANET 속성편집기 6.4 통합 뷰) =====

    /**
     * 객체별 속성이 흩어져 있는 섹션을 ID 로 join 해 노드/링크 {@code properties} 에 합친다.
     *
     * <p>EPANET 속성편집기(매뉴얼 6.4)는 한 객체를 보여줄 때 여러 섹션의 값을 모아 노출한다(예: 절점은
     * {@code [JUNCTIONS]} 외에 {@code [TAGS]}/{@code [DEMANDS]}/{@code [EMITTERS]}/{@code [QUALITY]}/
     * {@code [SOURCES]} 값을 함께 표시). 여기서 같은 통합을 수행한다. 객체 빌드 단계에서 6.4 전체 스키마를
     * 미리 {@code null} 로 깔아두므로, 이 병합은 해당 키에 실제 값을 채워 넣는다(데이터가 없으면 {@code null}
     * 유지 — 프론트가 키 유무 검사 없이 폼을 그릴 수 있다). 미참조 ID 는 경고로 모은다. 전역 설정(REACTIONS
     * 차수·전역계수, ENERGY 전역)은 {@code options} 가 담당하고, 여기서는 요소별/펌프별 값만 합친다.</p>
     */
    private void mergeAttributeSections(ParsedInp parsed, Map<String, NetworkNode> nodeById,
                                        Map<String, NetworkLink> linkById, List<String> warnings) {
        mergeTags(parsed, nodeById, linkById, warnings);
        mergeDemands(parsed, nodeById, warnings);
        mergeEmitters(parsed, nodeById, warnings);
        mergeQuality(parsed, nodeById, warnings);
        mergeSources(parsed, nodeById, warnings);
        mergeMixing(parsed, nodeById, warnings);
        mergeReactions(parsed, nodeById, linkById, warnings);
        mergeEnergy(parsed, linkById, warnings);
    }

    /** {@code [TAGS]} {@code <NODE|LINK> <id> <tag>} → 해당 객체의 {@code tag}. */
    private void mergeTags(ParsedInp parsed, Map<String, NetworkNode> nodeById,
                           Map<String, NetworkLink> linkById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.TAGS)) {
            String elementType = row.token(0);
            String id = row.token(1);
            String tag = joinFrom(row, 2);
            if (id == null || tag == null || tag.isBlank()) {
                continue;
            }
            boolean linkFirst = elementType != null && elementType.equalsIgnoreCase("LINK");
            if (!mergeTagInto(id, tag, linkFirst, nodeById, linkById)) {
                orphan++;
            }
        }
        if (orphan > 0) {
            warnings.add("[TAGS] 존재하지 않는 객체를 참조하는 태그 " + orphan + "건");
        }
    }

    /** 태그를 노드/링크 중 존재하는 쪽에 넣는다(LINK 키워드면 링크 우선). 어디에도 없으면 {@code false}. */
    private boolean mergeTagInto(String id, String tag, boolean linkFirst,
                                 Map<String, NetworkNode> nodeById, Map<String, NetworkLink> linkById) {
        NetworkNode node = nodeById.get(id);
        NetworkLink link = linkById.get(id);
        if (linkFirst && link != null) {
            link.getProperties().put("tag", tag);
            return true;
        }
        if (node != null) {
            node.getProperties().put("tag", tag);
            return true;
        }
        if (link != null) {
            link.getProperties().put("tag", tag);
            return true;
        }
        return false;
    }

    /** {@code [DEMANDS]} {@code <id> <demand> <pattern> [category]} → 절점의 {@code demandCategories} 리스트. */
    private void mergeDemands(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        Map<String, List<Map<String, Object>>> byId = new LinkedHashMap<>();
        for (SectionRow row : parsed.rows(InpSectionType.DEMANDS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            if (!nodeById.containsKey(id)) {
                orphan++;
                continue;
            }
            // 6.5 Demand Editor 라벨: Base Demand / Time Pattern / Category
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("baseDemand", numOrStr(row.token(1)));
            category.put("timePattern", row.token(2));
            // Category: 4번째 토큰 우선, 없으면 인라인 주석(EPANET 은 카테고리를 주석으로 쓰기도 함)
            String name = row.token(3);
            category.put("category", name != null ? name : blankToNull(row.inlineComment()));
            byId.computeIfAbsent(id, k -> new ArrayList<>()).add(category);
        }
        byId.forEach((id, categories) -> nodeById.get(id).getProperties().put("demandCategories", categories));
        if (orphan > 0) {
            warnings.add("[DEMANDS] 존재하지 않는 노드를 참조하는 수요 " + orphan + "건");
        }
    }

    /** {@code [EMITTERS]} {@code <id> <coefficient>} → 절점의 {@code emitterCoefficient}. */
    private void mergeEmitters(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.EMITTERS)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            orphan += putNodeProp(nodeById, id, "emitterCoefficient", numOrStr(row.token(1)));
        }
        if (orphan > 0) {
            warnings.add("[EMITTERS] 존재하지 않는 노드를 참조하는 이미터 " + orphan + "건");
        }
    }

    /** {@code [QUALITY]} {@code <node> <initQuality>} → 노드의 {@code initialQuality}. */
    private void mergeQuality(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.QUALITY)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            orphan += putNodeProp(nodeById, id, "initialQuality", numOrStr(row.token(1)));
        }
        if (orphan > 0) {
            warnings.add("[QUALITY] 존재하지 않는 노드를 참조하는 초기수질 " + orphan + "건");
        }
    }

    /**
     * {@code [SOURCES]} {@code <node> <type> <quality> [pattern]} → 노드의 {@code sourceQuality} 맵.
     * 내부 키는 6.5 Source Quality Editor(Table 6.10) 라벨: {@code sourceType}/{@code sourceQuality}/{@code qualityPattern}.
     */
    private void mergeSources(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.SOURCES)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            NetworkNode node = nodeById.get(id);
            if (node == null) {
                orphan++;
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("sourceType", EnumNormalizer.upper(row.token(1))); // Source Type enum(CONCEN/MASS/FLOWPACED/SETPOINT) → 대문자 통일
            source.put("sourceQuality", numOrStr(row.token(2)));  // Source Quality
            source.put("qualityPattern", row.token(3));    // Quality Pattern
            node.getProperties().put("sourceQuality", source);
        }
        if (orphan > 0) {
            warnings.add("[SOURCES] 존재하지 않는 노드를 참조하는 소스 " + orphan + "건");
        }
    }

    /** {@code [MIXING]} {@code <tank> <model> [fraction]} → 탱크의 {@code mixingModel}/{@code mixingFraction}. */
    private void mergeMixing(ParsedInp parsed, Map<String, NetworkNode> nodeById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.MIXING)) {
            String id = row.id();
            if (id == null) {
                continue;
            }
            NetworkNode node = nodeById.get(id);
            if (node == null) {
                orphan++;
                continue;
            }
            node.getProperties().put("mixingModel", EnumNormalizer.upper(row.token(1))); // 혼합모델 enum(MIXED/2COMP/FIFO/LIFO) → 대문자 통일
            if (row.token(2) != null) {
                node.getProperties().put("mixingFraction", numOrStr(row.token(2)));
            }
        }
        if (orphan > 0) {
            warnings.add("[MIXING] 존재하지 않는 탱크를 참조하는 혼합모델 " + orphan + "건");
        }
    }

    /**
     * {@code [REACTIONS]} 의 요소별 계수만 객체에 합친다(전역/차수는 {@code options.reactions} 가 담당).
     * 키는 6.4 라벨: {@code BULK <pipe> c}→관로 {@code bulkCoefficient}, {@code WALL <pipe> c}→관로
     * {@code wallCoefficient}, {@code TANK <tank> c}→탱크 {@code reactionCoefficient}.
     */
    private void mergeReactions(ParsedInp parsed, Map<String, NetworkNode> nodeById,
                                Map<String, NetworkLink> linkById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.REACTIONS)) {
            String keyword = row.token(0);
            if (keyword == null) {
                continue;
            }
            String id = row.token(1);
            Object coeff = numOrStr(row.token(2));
            switch (keyword.toUpperCase(Locale.ROOT)) {
                case "BULK" -> orphan += putLinkProp(linkById, id, "bulkCoefficient", coeff);
                case "WALL" -> orphan += putLinkProp(linkById, id, "wallCoefficient", coeff);
                case "TANK" -> orphan += putNodeProp(nodeById, id, "reactionCoefficient", coeff);
                default -> { /* ORDER/GLOBAL/LIMITING/ROUGHNESS 는 전역 → options.reactions 가 담당 */ }
            }
        }
        if (orphan > 0) {
            warnings.add("[REACTIONS] 존재하지 않는 객체를 참조하는 반응계수 " + orphan + "건");
        }
    }

    /**
     * {@code [ENERGY]} 의 펌프별 항목만 펌프에 합친다(전역은 {@code options.energy} 가 담당).
     * {@code PUMP <id> EFFIC <curve>}→{@code efficiencyCurve}, {@code PRICE <v>}→{@code energyPrice},
     * {@code PATTERN <id>}→{@code pricePattern}.
     */
    private void mergeEnergy(ParsedInp parsed, Map<String, NetworkLink> linkById, List<String> warnings) {
        int orphan = 0;
        for (SectionRow row : parsed.rows(InpSectionType.ENERGY)) {
            if (!"PUMP".equalsIgnoreCase(row.token(0))) {
                continue;
            }
            String id = row.token(1);
            String param = row.token(2);
            if (id == null || param == null) {
                continue;
            }
            NetworkLink pump = linkById.get(id);
            if (pump == null) {
                orphan++;
                continue;
            }
            switch (param.toUpperCase(Locale.ROOT)) {
                case "EFFIC" -> pump.getProperties().put("efficiencyCurve", joinFrom(row, 3));
                case "PRICE" -> pump.getProperties().put("energyPrice", numOrStr(joinFrom(row, 3)));
                case "PATTERN" -> pump.getProperties().put("pricePattern", joinFrom(row, 3));
                default -> { /* 알 수 없는 파라미터는 무시 */ }
            }
        }
        if (orphan > 0) {
            warnings.add("[ENERGY] 존재하지 않는 펌프를 참조하는 항목 " + orphan + "건");
        }
    }

    /** 링크 속성에 값을 넣는다. 미참조면 1(경고 카운트)을 반환. */
    private int putLinkProp(Map<String, NetworkLink> linkById, String id, String key, Object value) {
        NetworkLink link = (id == null) ? null : linkById.get(id);
        if (link == null) {
            return 1;
        }
        link.getProperties().put(key, value);
        return 0;
    }

    /** 노드 속성에 값을 넣는다. 미참조면 1(경고 카운트)을 반환. */
    private int putNodeProp(Map<String, NetworkNode> nodeById, String id, String key, Object value) {
        NetworkNode node = (id == null) ? null : nodeById.get(id);
        if (node == null) {
            return 1;
        }
        node.getProperties().put(key, value);
        return 0;
    }

    // ===== 라벨 =====

    private List<MapLabel> buildLabels(ParsedInp parsed) {
        List<MapLabel> labels = new ArrayList<>();
        for (SectionRow row : parsed.rows(InpSectionType.LABELS)) {
            Double x = parseDouble(row.token(0));
            Double y = parseDouble(row.token(1));
            String text = row.token(2);
            if (x == null || y == null) {
                continue;
            }
            String anchor = row.token(3);
            labels.add(new MapLabel(new Coord(x, y), text == null ? "" : text, anchor));
        }
        return labels;
    }

    // ===== 경고 / 경계 =====

    private void collectGeometryWarnings(List<NetworkNode> nodes, List<NetworkLink> links,
                                         Map<String, NetworkNode> nodeById, List<String> warnings) {
        long missingNodeCoord = nodes.stream().filter(n -> !n.hasCoord()).count();
        if (missingNodeCoord > 0) {
            warnings.add("좌표가 없는 노드 " + missingNodeCoord + "개(점 미표출)");
        }
        long brokenLinks = links.stream().filter(l -> !hasEndpointCoords(l, nodeById)).count();
        if (brokenLinks > 0) {
            warnings.add("끝점 좌표가 없어 선을 그릴 수 없는 링크 " + brokenLinks + "개");
        }
    }

    /** 링크의 양 끝 노드가 모두 좌표를 가지는지 검사한다. */
    private boolean hasEndpointCoords(NetworkLink link, Map<String, NetworkNode> nodeById) {
        NetworkNode n1 = nodeById.get(link.getNode1Id());
        NetworkNode n2 = nodeById.get(link.getNode2Id());
        return n1 != null && n1.hasCoord() && n2 != null && n2.hasCoord();
    }

    private NetworkBounds computeBounds(List<NetworkNode> nodes, List<NetworkLink> links, List<MapLabel> labels) {
        Envelope env = new Envelope();
        for (NetworkNode node : nodes) {
            if (node.hasCoord()) {
                env.expandToInclude(node.getCoord().x(), node.getCoord().y());
            }
        }
        for (NetworkLink link : links) {
            for (Coord v : link.getVertices()) {
                env.expandToInclude(v.x(), v.y());
            }
        }
        for (MapLabel label : labels) {
            env.expandToInclude(label.coord().x(), label.coord().y());
        }
        return NetworkBounds.from(env);
    }

    private NetworkBounds parseBackdrop(ParsedInp parsed) {
        for (SectionRow row : parsed.rows(InpSectionType.BACKDROP)) {
            if ("DIMENSIONS".equalsIgnoreCase(row.token(0))) {
                Double minX = parseDouble(row.token(1));
                Double minY = parseDouble(row.token(2));
                Double maxX = parseDouble(row.token(3));
                Double maxY = parseDouble(row.token(4));
                if (minX != null && minY != null && maxX != null && maxY != null) {
                    return new NetworkBounds(minX, minY, maxX, maxY);
                }
            }
        }
        return null;
    }

    // ===== 공통 헬퍼 =====

    /** 노드를 목록과 인덱스에 등록한다(ID 중복 시 첫 항목 유지, 인덱스는 마지막 값으로 덮어쓰지 않음). */
    private void register(NetworkNode node, List<NetworkNode> nodes, Map<String, NetworkNode> index) {
        nodes.add(node);
        index.putIfAbsent(node.getId(), node);
    }

    private void register(NetworkLink link, List<NetworkLink> links, Map<String, NetworkLink> index) {
        links.add(link);
        index.putIfAbsent(link.getId(), link);
    }

    /** 숫자로 파싱되면 {@link Double}, 안 되면 원본 문자열, 토큰이 없으면 {@code null} 을 맵에 넣는다. */
    private void putNum(Map<String, Object> props, String key, String token) {
        if (token == null) {
            props.put(key, null);
            return;
        }
        Double d = parseDouble(token);
        props.put(key, d != null ? d : token);
    }

    /** 문자열 값을 그대로 넣는다(토큰 없으면 {@code null}). */
    private void putStr(Map<String, Object> props, String key, String token) {
        props.put(key, token);
    }

    /** 객체의 인라인 주석을 설명({@code description})으로 넣는다(6.4 전체 스키마 고정 — 비어 있으면 {@code null}). */
    private void putDesc(Map<String, Object> props, SectionRow row) {
        props.put("description", blankToNull(row.inlineComment()));
    }

    /** 숫자로 파싱되면 {@link Double}, 안 되면 원본 문자열, 토큰이 없으면 {@code null}. */
    private Object numOrStr(String token) {
        if (token == null) {
            return null;
        }
        Double d = parseDouble(token);
        return d != null ? d : token;
    }

    /** {@code start} 이후 토큰을 공백으로 이어 붙인다(없으면 {@code null}). */
    private String joinFrom(SectionRow row, int start) {
        if (start >= row.size()) {
            return null;
        }
        return String.join(" ", row.tokens().subList(start, row.size()));
    }

    /** 공백/빈 문자열은 {@code null} 로 정규화한다. */
    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 안전한 double 파싱. 실패 시 {@code null}. */
    private Double parseDouble(String token) {
        if (token == null) {
            return null;
        }
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
