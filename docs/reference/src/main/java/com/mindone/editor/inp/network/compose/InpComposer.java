package com.mindone.editor.inp.network.compose;

import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.geojson.GeoJsonFeature;
import com.mindone.editor.inp.network.geojson.GeoJsonFeatureCollection;
import com.mindone.editor.inp.network.geojson.GeoJsonGeometry;
import com.mindone.editor.inp.network.model.NetworkBounds;
import com.mindone.editor.inp.network.dto.NetworkMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 상세조회 응답({@link NetworkSaveRequest})을 전 섹션 토큰 행({@link InpDocument})으로 역변환한다(Compose 단계).
 *
 * <p>상세조회의 결합 과정({@code GeoJsonCombiner}/{@code NonVisualSectionCombiner}/{@code OptionsCombiner}/
 * {@code NetworkAssembler})을 그대로 뒤집는다. 가시 객체 properties 에 통합돼 있던 객체별 속성 섹션
 * (TAGS/DEMANDS/STATUS/EMITTERS/QUALITY/SOURCES/MIXING/REACTIONS·ENERGY 의 요소별)을 다시 해당 섹션으로
 * 분리하고, 좌표/정점/라벨은 GeoJSON 기하에서 복원한다. 설정(options)은 EPANET 키워드로 되돌린다.</p>
 *
 * <p>섹션 기재 순서는 EPANET/WNTR 표준을 따른다. 값은 {@link InpValueFormatter} 로 표기하고, 비어 있는
 * 섹션도 출력해 원본의 빈 섹션 표기를 보존한다.</p>
 */
@Component
public class InpComposer {

    /**
     * 저장 요청을 INP 문서로 역변환한다.
     *
     * @param request 편집된 상세조회 응답
     * @return 전 섹션이 복원된 INP 문서
     */
    public InpDocument compose(NetworkSaveRequest request) {
        InpDocument doc = new InpDocument();

        List<GeoJsonFeature> nodes = features(request.layers() == null ? null : request.layers().nodeLayer());
        List<GeoJsonFeature> links = features(request.layers() == null ? null : request.layers().linkLayer());
        List<GeoJsonFeature> labels = features(request.layers() == null ? null : request.layers().labelLayer());
        Map<String, Object> sections = nullToEmpty(request.sections());
        // 설정은 sections 의 OPTIONS 키에 담겨 온다(상세조회 응답과 동일 구조).
        Map<String, Object> options = asMap(sections.get("OPTIONS"));

        title(doc, sections);
        junctions(doc, nodes);
        reservoirs(doc, nodes);
        tanks(doc, nodes);
        pipes(doc, links);
        pumps(doc, links);
        valves(doc, links);
        tags(doc, nodes, links);
        demands(doc, nodes);
        status(doc, links);
        patterns(doc, sections);
        curves(doc, sections);
        controls(doc, sections);
        rules(doc, sections);
        energy(doc, links, options);
        emitters(doc, nodes);
        quality(doc, nodes);
        sources(doc, nodes);
        reactions(doc, links, nodes, options);
        mixing(doc, nodes);
        times(doc, options);
        report(doc, sections);
        optionsSection(doc, options);
        coordinates(doc, nodes);
        vertices(doc, links);
        labelsSection(doc, labels);
        backdrop(doc, request.meta());
        doc.addSection("END");

        return doc;
    }

    // ===== 가시 객체: 노드 =====

    private void junctions(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("JUNCTIONS");
        for (GeoJsonFeature f : byType(nodes, "junction")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmtOr(p.get("elevation"), "0"));
            Object demand = p.get("baseDemand");
            Object pattern = p.get("demandPattern");
            if (demand != null || pattern != null) {
                t.add(fmtOr(demand, "0"));
                if (pattern != null) {
                    t.add(fmt(pattern));
                }
            }
            s.add(t, desc(p));
        }
    }

    private void reservoirs(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("RESERVOIRS");
        for (GeoJsonFeature f : byType(nodes, "reservoir")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmtOr(p.get("totalHead"), "0"));
            Object pattern = p.get("headPattern");
            if (pattern != null) {
                t.add(fmt(pattern));
            }
            s.add(t, desc(p));
        }
    }

    private void tanks(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("TANKS");
        for (GeoJsonFeature f : byType(nodes, "tank")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmtOr(p.get("elevation"), "0"));
            t.add(fmtOr(p.get("initialLevel"), "0"));
            t.add(fmtOr(p.get("minimumLevel"), "0"));
            t.add(fmtOr(p.get("maximumLevel"), "0"));
            t.add(fmtOr(p.get("diameter"), "0"));
            t.add(fmtOr(p.get("minimumVolume"), "0"));
            Object volumeCurve = p.get("volumeCurve");
            Object canOverflow = p.get("canOverflow");
            if (volumeCurve != null || canOverflow != null) {
                t.add(volumeCurve != null ? fmt(volumeCurve) : "*");
                if (canOverflow != null) {
                    t.add(fmt(canOverflow));
                }
            }
            s.add(t, desc(p));
        }
    }

    // ===== 가시 객체: 링크 =====

    private void pipes(InpDocument doc, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("PIPES");
        for (GeoJsonFeature f : byType(links, "pipe")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmt(p.get("startNode")));
            t.add(fmt(p.get("endNode")));
            t.add(fmtOr(p.get("length"), "0"));
            t.add(fmtOr(p.get("diameter"), "0"));
            t.add(fmtOr(p.get("roughness"), "0"));
            t.add(fmtOr(p.get("lossCoefficient"), "0"));
            Object initialStatus = p.get("initialStatus");
            if (initialStatus != null) {
                t.add(fmt(initialStatus)); // 관로 상태는 자체 컬럼(STATUS 섹션 아님)
            }
            s.add(t, desc(p));
        }
    }

    private void pumps(InpDocument doc, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("PUMPS");
        for (GeoJsonFeature f : byType(links, "pump")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmt(p.get("startNode")));
            t.add(fmt(p.get("endNode")));
            addKeyword(t, "HEAD", p.get("pumpCurve"));
            addKeyword(t, "POWER", p.get("power"));
            addKeyword(t, "SPEED", p.get("speed"));
            addKeyword(t, "PATTERN", p.get("pattern"));
            s.add(t, desc(p));
        }
    }

    private void valves(InpDocument doc, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("VALVES");
        for (GeoJsonFeature f : byType(links, "valve")) {
            Map<String, Object> p = props(f);
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmt(p.get("startNode")));
            t.add(fmt(p.get("endNode")));
            t.add(fmtOr(p.get("diameter"), "0"));
            t.add(fmtOr(p.get("type"), "GPV"));
            t.add(fmtOr(p.get("setting"), "0"));
            t.add(fmtOr(p.get("lossCoefficient"), "0"));
            s.add(t, desc(p));
        }
    }

    // ===== 객체별 속성 섹션(properties 에서 다시 분리) =====

    private void tags(InpDocument doc, List<GeoJsonFeature> nodes, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("TAGS");
        for (GeoJsonFeature f : nodes) {
            Object tag = props(f).get("tag");
            if (tag != null) {
                s.add(List.of("NODE", fmt(props(f).get("id")), fmt(tag)));
            }
        }
        for (GeoJsonFeature f : links) {
            Object tag = props(f).get("tag");
            if (tag != null) {
                s.add(List.of("LINK", fmt(props(f).get("id")), fmt(tag)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void demands(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("DEMANDS");
        for (GeoJsonFeature f : nodes) {
            Map<String, Object> p = props(f);
            Object categories = p.get("demandCategories");
            if (!(categories instanceof List<?> list)) {
                continue;
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> cat)) {
                    continue;
                }
                Map<String, Object> c = (Map<String, Object>) cat;
                List<String> t = new ArrayList<>();
                t.add(fmt(p.get("id")));
                t.add(fmtOr(c.get("baseDemand"), "0"));
                Object timePattern = c.get("timePattern");
                if (timePattern != null) {
                    t.add(fmt(timePattern));
                }
                s.add(t, str(c.get("category"))); // 카테고리명은 인라인 주석으로(파서가 주석에서도 읽음)
            }
        }
    }

    private void status(InpDocument doc, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("STATUS");
        for (GeoJsonFeature f : links) {
            Map<String, Object> p = props(f);
            String type = type(f);
            if ("pump".equals(type)) {
                Object st = p.get("initialStatus");
                if (st != null) {
                    s.add(List.of(fmt(p.get("id")), fmt(st)));
                }
            } else if ("valve".equals(type)) {
                Object st = p.get("fixedStatus");
                if (st != null) {
                    s.add(List.of(fmt(p.get("id")), fmt(st)));
                }
            }
            // 관로 상태는 PIPES 자체 컬럼으로 기재되므로 STATUS 에 중복하지 않는다.
        }
    }

    private void emitters(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("EMITTERS");
        for (GeoJsonFeature f : nodes) {
            Object coeff = props(f).get("emitterCoefficient");
            if (coeff != null) {
                s.add(List.of(fmt(props(f).get("id")), fmt(coeff)));
            }
        }
    }

    private void quality(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("QUALITY");
        for (GeoJsonFeature f : nodes) {
            Object q = props(f).get("initialQuality");
            if (q != null) {
                s.add(List.of(fmt(props(f).get("id")), fmt(q)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void sources(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("SOURCES");
        for (GeoJsonFeature f : nodes) {
            Object src = props(f).get("sourceQuality");
            if (!(src instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> source = (Map<String, Object>) m;
            List<String> t = new ArrayList<>();
            t.add(fmt(props(f).get("id")));
            t.add(fmtOr(source.get("sourceType"), "CONCEN"));
            t.add(fmtOr(source.get("sourceQuality"), "0"));
            Object pattern = source.get("qualityPattern");
            if (pattern != null) {
                t.add(fmt(pattern));
            }
            s.add(t);
        }
    }

    private void mixing(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("MIXING");
        for (GeoJsonFeature f : byType(nodes, "tank")) {
            Map<String, Object> p = props(f);
            Object model = p.get("mixingModel");
            if (model == null) {
                continue;
            }
            List<String> t = new ArrayList<>();
            t.add(fmt(p.get("id")));
            t.add(fmt(model));
            Object fraction = p.get("mixingFraction");
            if (fraction != null) {
                t.add(fmt(fraction));
            }
            s.add(t);
        }
    }

    // ===== 비가시 섹션 =====

    private void title(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("TITLE");
        if (sections.get("TITLE") instanceof List<?> lines) {
            for (Object line : lines) {
                s.addRaw(str(line));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void patterns(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("PATTERNS");
        if (!(sections.get("PATTERNS") instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> pattern = (Map<String, Object>) m;
            String id = fmt(pattern.get("id"));
            String description = str(pattern.get("description"));
            if (description != null) {
                s.addRaw(";" + description); // 직전 주석(설명) 보존
            }
            List<?> multipliers = pattern.get("multipliers") instanceof List<?> l ? l : List.of();
            // EPANET 관행: 한 줄당 최대 6개 배율
            for (int i = 0; i < multipliers.size(); i += 6) {
                List<String> t = new ArrayList<>();
                t.add(id);
                for (int j = i; j < Math.min(i + 6, multipliers.size()); j++) {
                    t.add(fmt(multipliers.get(j)));
                }
                s.add(t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void curves(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("CURVES");
        if (!(sections.get("CURVES") instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> curve = (Map<String, Object>) m;
            String id = fmt(curve.get("id"));
            String curveType = str(curve.get("curveType"));
            String description = str(curve.get("description"));
            if (description != null) {
                // 파서가 ;<타입>: <설명> 에서 타입/설명을 복원하므로 그 형태로 기재
                s.addRaw(";" + (curveType != null ? curveType + ": " : "") + description);
            }
            if (curve.get("xyData") instanceof List<?> points) {
                for (Object pt : points) {
                    if (pt instanceof Map<?, ?> pm) {
                        Map<String, Object> point = (Map<String, Object>) pm;
                        s.add(List.of(id, fmtOr(point.get("x"), "0"), fmtOr(point.get("y"), "0")));
                    }
                }
            }
        }
    }

    /** CONTROLS 는 {@code {simple, rule}} 묶음으로 오며, 단순 제어문은 {@code simple} 에서 복원한다. */
    private void controls(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("CONTROLS");
        if (asMap(sections.get("CONTROLS")).get("simple") instanceof List<?> lines) {
            for (Object line : lines) {
                s.addRaw(str(line));
            }
        }
    }

    /** 규칙 제어는 CONTROLS 묶음의 {@code rule} 에서 복원해 별도 [RULES] 섹션으로 되돌린다. */
    @SuppressWarnings("unchecked")
    private void rules(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("RULES");
        if (!(asMap(sections.get("CONTROLS")).get("rule") instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) m;
            s.addRaw("RULE " + fmt(rule.get("id")));
            String content = str(rule.get("content"));
            if (content != null && !content.isEmpty()) {
                for (String line : content.split("\n", -1)) {
                    s.addRaw(line); // 본문은 IF/AND/THEN/PRIORITY 절을 원문 그대로 보존
                }
            }
        }
    }

    private void report(InpDocument doc, Map<String, Object> sections) {
        InpSection s = doc.addSection("REPORT");
        if (sections.get("REPORT") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                List<String> t = new ArrayList<>();
                t.add(str(e.getKey()));
                addSplit(t, str(e.getValue()));
                s.add(t);
            }
        }
    }

    // ===== 설정(options) =====

    private void energy(InpDocument doc, List<GeoJsonFeature> links, Map<String, Object> options) {
        InpSection s = doc.addSection("ENERGY");
        Map<String, Object> e = asMap(options.get("energy"));
        addKv(s, "GLOBAL EFFICIENCY", e.get("pumpEfficiency"));
        addKv(s, "GLOBAL PRICE", e.get("energyPrice"));
        addKv(s, "GLOBAL PATTERN", e.get("pricePattern"));
        addKv(s, "DEMAND CHARGE", e.get("demandCharge"));
        // 펌프별 항목: 펌프 객체 properties 에서 다시 분리(전역과 키워드로 구분되어 round-trip 안전)
        for (GeoJsonFeature f : byType(links, "pump")) {
            Map<String, Object> p = props(f);
            String id = fmt(p.get("id"));
            addPumpEnergy(s, id, "EFFIC", p.get("efficiencyCurve"));
            addPumpEnergy(s, id, "PRICE", p.get("energyPrice"));
            addPumpEnergy(s, id, "PATTERN", p.get("pricePattern"));
        }
    }

    private void reactions(InpDocument doc, List<GeoJsonFeature> links, List<GeoJsonFeature> nodes,
                           Map<String, Object> options) {
        InpSection s = doc.addSection("REACTIONS");
        Map<String, Object> r = asMap(options.get("reactions"));
        addKv2(s, "ORDER", "BULK", r.get("bulkReactionOrder"));
        addKv2(s, "ORDER", "TANK", r.get("tankReactionOrder"));
        addKv2(s, "ORDER", "WALL", r.get("wallReactionOrder"));
        addKv2(s, "GLOBAL", "BULK", r.get("globalBulkCoefficient"));
        addKv2(s, "GLOBAL", "WALL", r.get("globalWallCoefficient"));
        addKv2(s, "LIMITING", "POTENTIAL", r.get("limitingConcentration"));
        addKv2(s, "ROUGHNESS", "CORRELATION", r.get("wallCoefficientCorrelation"));
        // 요소별 계수: 객체 properties 에서 다시 분리(전역과 키워드로 구분되어 round-trip 안전)
        for (GeoJsonFeature f : links) {
            Map<String, Object> p = props(f);
            addElementCoeff(s, "BULK", p.get("id"), p.get("bulkCoefficient"));
            addElementCoeff(s, "WALL", p.get("id"), p.get("wallCoefficient"));
        }
        for (GeoJsonFeature f : byType(nodes, "tank")) {
            Map<String, Object> p = props(f);
            addElementCoeff(s, "TANK", p.get("id"), p.get("reactionCoefficient"));
        }
    }

    private void times(InpDocument doc, Map<String, Object> options) {
        InpSection s = doc.addSection("TIMES");
        Map<String, Object> t = asMap(options.get("times"));
        for (Map.Entry<String, Object> e : t.entrySet()) {
            if (e.getValue() == null) {
                continue; // 값이 없는 항목은 INP 줄을 만들지 않는다(전체 스키마 고정으로 null 키가 온다)
            }
            String keyword = TIME_KEYWORDS.getOrDefault(e.getKey(), e.getKey());
            List<String> tokens = new ArrayList<>();
            addSplit(tokens, keyword);
            addSplit(tokens, str(e.getValue()));
            s.add(tokens);
        }
    }

    private void optionsSection(InpDocument doc, Map<String, Object> options) {
        InpSection s = doc.addSection("OPTIONS");
        Map<String, Object> h = asMap(options.get("hydraulics"));
        for (Map.Entry<String, Object> e : h.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            String keyword = OPTION_KEYWORDS.getOrDefault(e.getKey(), e.getKey().toUpperCase(java.util.Locale.ROOT));
            List<String> tokens = new ArrayList<>();
            addSplit(tokens, keyword);
            addSplit(tokens, str(e.getValue()));
            s.add(tokens);
        }
        Map<String, Object> q = asMap(options.get("quality"));
        Object parameter = q.get("parameter");
        if (parameter != null) {
            List<String> tokens = new ArrayList<>();
            tokens.add("QUALITY");
            tokens.add(fmt(parameter));
            if (q.get("massUnits") != null) {
                tokens.add(fmt(q.get("massUnits")));
            } else if (q.get("traceNode") != null) {
                tokens.add(fmt(q.get("traceNode")));
            }
            s.add(tokens);
        }
        addKv(s, "DIFFUSIVITY", q.get("relativeDiffusivity"));
        addKv(s, "TOLERANCE", q.get("qualityTolerance"));
    }

    // ===== 지도/표출 =====

    private void coordinates(InpDocument doc, List<GeoJsonFeature> nodes) {
        InpSection s = doc.addSection("COORDINATES");
        for (GeoJsonFeature f : nodes) {
            double[] p = point(f.geometry());
            if (p != null) {
                s.add(List.of(fmt(props(f).get("id")), coord(p[0]), coord(p[1])));
            }
        }
    }

    private void vertices(InpDocument doc, List<GeoJsonFeature> links) {
        InpSection s = doc.addSection("VERTICES");
        for (GeoJsonFeature f : links) {
            List<double[]> line = lineString(f.geometry());
            if (line.size() <= 2) {
                continue; // 양 끝(노드 좌표)만 있으면 중간 정점 없음
            }
            String id = fmt(props(f).get("id"));
            for (int i = 1; i < line.size() - 1; i++) { // 시작/끝 노드 좌표 제외
                s.add(List.of(id, coord(line.get(i)[0]), coord(line.get(i)[1])));
            }
        }
    }

    private void labelsSection(InpDocument doc, List<GeoJsonFeature> labels) {
        InpSection s = doc.addSection("LABELS");
        for (GeoJsonFeature f : labels) {
            double[] p = point(f.geometry());
            if (p == null) {
                continue;
            }
            Map<String, Object> props = props(f);
            List<String> t = new ArrayList<>();
            t.add(coord(p[0]));
            t.add(coord(p[1]));
            t.add("\"" + str0(props.get("text")) + "\""); // 라벨 텍스트는 따옴표로 감싼다(공백 보호)
            Object anchor = props.get("anchorNode");
            if (anchor != null) {
                t.add(fmt(anchor));
            }
            s.add(t);
        }
    }

    private void backdrop(InpDocument doc, NetworkMeta meta) {
        InpSection s = doc.addSection("BACKDROP");
        if (meta == null || meta.backdropBounds() == null) {
            return;
        }
        NetworkBounds b = meta.backdropBounds();
        s.add(List.of("DIMENSIONS", coord(b.minX()), coord(b.minY()), coord(b.maxX()), coord(b.maxY())));
        s.add(List.of("UNITS", "NONE"));
        s.add(List.of("OFFSET", "0.00", "0.00"));
    }

    // ===== 토큰 조립 헬퍼 =====

    /** keyword + 값(있을 때만) 토큰을 추가한다. */
    private void addKeyword(List<String> tokens, String keyword, Object value) {
        if (value != null) {
            tokens.add(keyword);
            tokens.add(fmt(value));
        }
    }

    /** "GLOBAL EFFICIENCY" 처럼 키워드(공백 포함) + 단일 값 행을 추가한다(값 없으면 생략). */
    private void addKv(InpSection s, String keyword, Object value) {
        if (value == null) {
            return;
        }
        List<String> t = new ArrayList<>();
        addSplit(t, keyword);
        addSplit(t, str(value));
        s.add(t);
    }

    /** "ORDER BULK 1" 처럼 키워드 2개 + 단일 값 행을 추가한다(값 없으면 생략). */
    private void addKv2(InpSection s, String k1, String k2, Object value) {
        if (value == null) {
            return;
        }
        s.add(List.of(k1, k2, fmt(value)));
    }

    /** "PUMP <id> EFFIC <curve>" 펌프별 에너지 행을 추가한다(값 없으면 생략). */
    private void addPumpEnergy(InpSection s, String id, String param, Object value) {
        if (value != null) {
            s.add(List.of("PUMP", id, param, fmt(value)));
        }
    }

    /** "BULK <id> <c>" 요소별 반응계수 행을 추가한다(값 없으면 생략). */
    private void addElementCoeff(InpSection s, String keyword, Object id, Object value) {
        if (value != null) {
            s.add(List.of(keyword, fmt(id), fmt(value)));
        }
    }

    /** 공백이 섞인 값을 토큰들로 쪼개 추가한다(빈 문자열은 무시). */
    private void addSplit(List<String> tokens, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (String part : value.split("\\s+")) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
    }

    // ===== 조회/변환 헬퍼 =====

    /** 특정 objectType 의 Feature 만 등장 순서대로 추린다. */
    private List<GeoJsonFeature> byType(List<GeoJsonFeature> features, String objectType) {
        List<GeoJsonFeature> out = new ArrayList<>();
        for (GeoJsonFeature f : features) {
            if (objectType.equals(type(f))) {
                out.add(f);
            }
        }
        return out;
    }

    /** Feature 의 objectType(소문자). */
    private String type(GeoJsonFeature f) {
        return str(props(f).get("objectType"));
    }

    /** FeatureCollection 의 Feature 목록(없으면 빈 목록). */
    private List<GeoJsonFeature> features(GeoJsonFeatureCollection fc) {
        if (fc == null || fc.features() == null) {
            return Collections.emptyList();
        }
        return fc.features();
    }

    /** Feature 속성 맵(없으면 빈 맵). */
    private Map<String, Object> props(GeoJsonFeature f) {
        return f == null || f.properties() == null ? Collections.emptyMap() : f.properties();
    }

    /** 기하에서 점 좌표 {@code [x, y]} 를 뽑는다(Point 가 아니면 {@code null}). */
    private double[] point(GeoJsonGeometry g) {
        if (g == null || g.coordinates() == null) {
            return null;
        }
        return pair(g.coordinates());
    }

    /** 기하에서 선 좌표 목록을 뽑는다(LineString 이 아니면 빈 목록). */
    private List<double[]> lineString(GeoJsonGeometry g) {
        List<double[]> out = new ArrayList<>();
        if (g == null || !(g.coordinates() instanceof List<?> coords)) {
            return out;
        }
        for (Object o : coords) {
            double[] p = pair(o);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** 좌표쌍(List/배열) 에서 {@code [x, y]} 를 뽑는다. */
    private double[] pair(Object o) {
        if (o instanceof List<?> l && l.size() >= 2 && l.get(0) instanceof Number x && l.get(1) instanceof Number y) {
            return new double[]{x.doubleValue(), y.doubleValue()};
        }
        if (o instanceof double[] d && d.length >= 2) {
            return new double[]{d[0], d[1]};
        }
        if (o instanceof Object[] a && a.length >= 2 && a[0] instanceof Number x && a[1] instanceof Number y) {
            return new double[]{x.doubleValue(), y.doubleValue()};
        }
        return null;
    }

    /** 객체의 description 을 인라인 주석 문자열로(없으면 {@code null}). */
    private String desc(Map<String, Object> props) {
        return str(props.get("description"));
    }

    /** 값을 INP 토큰 문자열로 표기한다(없으면 {@code null}). */
    private String fmt(Object value) {
        return InpValueFormatter.format(value);
    }

    /** 값이 없으면 기본값으로 대체해 표기한다. */
    private String fmtOr(Object value, String fallback) {
        String s = InpValueFormatter.format(value);
        return s != null ? s : fallback;
    }

    /** 좌표값을 표기한다(지수표기/잉여 0 제거). */
    private String coord(double d) {
        return InpValueFormatter.format(d);
    }

    /** 임의 객체를 문자열로(없으면 {@code null}). */
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** 임의 객체를 문자열로(없으면 빈 문자열). */
    private String str0(Object o) {
        return o == null ? "" : o.toString();
    }

    /** {@code Map<String,Object>} 로 안전 변환(아니면 빈 맵). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    /** {@code null} 맵을 빈 맵으로 정규화. */
    private Map<String, Object> nullToEmpty(Map<String, Object> m) {
        return m == null ? Collections.emptyMap() : m;
    }

    /** [TIMES] 친화 키 → INP 키워드(공백 포함 가능). */
    private static final Map<String, String> TIME_KEYWORDS = Map.ofEntries(
            Map.entry("totalDuration", "DURATION"),
            Map.entry("hydraulicTimeStep", "HYDRAULIC TIMESTEP"),
            Map.entry("qualityTimeStep", "QUALITY TIMESTEP"),
            Map.entry("patternTimeStep", "PATTERN TIMESTEP"),
            Map.entry("patternStartTime", "PATTERN START"),
            Map.entry("reportingTimeStep", "REPORT TIMESTEP"),
            Map.entry("reportStartTime", "REPORT START"),
            Map.entry("startingTimeOfDay", "START CLOCKTIME"),
            Map.entry("ruleTimeStep", "RULE TIMESTEP"),
            Map.entry("statistic", "STATISTIC")
    );

    /** [OPTIONS] 수리 친화 키 → INP 키워드(공백 포함 가능). */
    private static final Map<String, String> OPTION_KEYWORDS = Map.ofEntries(
            Map.entry("flowUnits", "UNITS"),
            Map.entry("headlossFormula", "HEADLOSS"),
            Map.entry("specificGravity", "SPECIFIC GRAVITY"),
            Map.entry("relativeViscosity", "VISCOSITY"),
            Map.entry("maximumTrials", "TRIALS"),
            Map.entry("accuracy", "ACCURACY"),
            Map.entry("ifUnbalanced", "UNBALANCED"),
            Map.entry("defaultPattern", "PATTERN"),
            Map.entry("demandMultiplier", "DEMAND MULTIPLIER"),
            Map.entry("emitterExponent", "EMITTER EXPONENT"),
            Map.entry("demandModel", "DEMAND MODEL"),
            Map.entry("minimumPressure", "MINIMUM PRESSURE"),
            Map.entry("requiredPressure", "REQUIRED PRESSURE"),
            Map.entry("pressureExponent", "PRESSURE EXPONENT"),
            Map.entry("checkFreq", "CHECKFREQ"),
            Map.entry("maxCheck", "MAXCHECK"),
            Map.entry("dampLimit", "DAMPLIMIT"),
            Map.entry("maxHeadError", "HEADERROR"),
            Map.entry("maxFlowChange", "FLOWCHANGE")
    );
}
