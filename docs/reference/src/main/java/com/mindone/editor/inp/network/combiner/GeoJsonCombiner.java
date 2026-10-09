package com.mindone.editor.inp.network.combiner;

import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.geojson.GeoJsonFeature;
import com.mindone.editor.inp.network.geojson.GeoJsonFeatureCollection;
import com.mindone.editor.inp.network.geojson.GeoJsonGeometry;
import com.mindone.editor.inp.network.model.Coord;
import com.mindone.editor.inp.network.model.LinkType;
import com.mindone.editor.inp.network.model.MapLabel;
import com.mindone.editor.inp.network.model.NetworkLink;
import com.mindone.editor.inp.network.model.NetworkModel;
import com.mindone.editor.inp.network.model.NetworkNode;
import com.mindone.editor.inp.network.model.NodeType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link NetworkModel} 을 레이어별 GeoJSON FeatureCollection 으로 변환한다(Combine 2단계).
 *
 * <p>노드는 Point, 링크는 {@code 시작노드 → 정점 → 끝노드} 의 LineString 으로 만든다. 좌표를 만들 수
 * 없으면 geometry 를 {@code null} 로 두고 속성만 유지한다. 각 Feature 속성에 {@code layer}/{@code objectType}
 * 을 넣어 프론트 스타일링을 돕는다.</p>
 */
@Component
public class GeoJsonCombiner {

    /**
     * 네트워크 모델을 레이어별 FeatureCollection 으로 변환한다.
     *
     * @param model 좌표가 결합된 네트워크 모델
     * @return 레이어별 GeoJSON
     */
    public NetworkLayers toLayers(NetworkModel model) {
        // 링크 형상 계산을 위한 노드 좌표 인덱스
        Map<String, Coord> coordById = new LinkedHashMap<>();
        for (NetworkNode node : model.getNodes()) {
            if (node.hasCoord()) {
                coordById.put(node.getId(), node.getCoord());
            }
        }

        return new NetworkLayers(
                nodeLayer(model),
                linkLayer(model, coordById),
                labelLayer(model)
        );
    }

    /** 모든 노드(절점/저수지/탱크)를 Point Feature 로 묶은 노드 레이어를 만든다. */
    private GeoJsonFeatureCollection nodeLayer(NetworkModel model) {
        List<GeoJsonFeature> features = new ArrayList<>();
        for (NetworkNode node : model.getNodes()) {
            NodeType type = node.getType();
            GeoJsonGeometry geometry = node.hasCoord() ? GeoJsonGeometry.point(node.getCoord()) : null;
            Map<String, Object> props = baseProps(node.getId(), type.name(), type.layer());
            props.putAll(node.getProperties());
            features.add(GeoJsonFeature.of(geometry, props));
        }
        return GeoJsonFeatureCollection.of(features);
    }

    /** 모든 링크(관로/펌프/밸브)를 LineString Feature 로 묶은 링크 레이어를 만든다. */
    private GeoJsonFeatureCollection linkLayer(NetworkModel model, Map<String, Coord> coordById) {
        List<GeoJsonFeature> features = new ArrayList<>();
        for (NetworkLink link : model.getLinks()) {
            LinkType type = link.getType();
            GeoJsonGeometry geometry = buildLineGeometry(link, coordById);
            Map<String, Object> props = baseProps(link.getId(), type.name(), type.layer());
            props.put("startNode", link.getNode1Id());   // 6.4 라벨: Start Node
            props.put("endNode", link.getNode2Id());     // 6.4 라벨: End Node
            props.putAll(link.getProperties());
            features.add(GeoJsonFeature.of(geometry, props));
        }
        return GeoJsonFeatureCollection.of(features);
    }

    /** 라벨 Point FeatureCollection 을 만든다. */
    private GeoJsonFeatureCollection labelLayer(NetworkModel model) {
        List<GeoJsonFeature> features = new ArrayList<>();
        int seq = 0;
        for (MapLabel label : model.getLabels()) {
            Map<String, Object> props = baseProps("label-" + (seq++), "LABEL", "labels");
            props.put("text", label.text());
            props.put("anchorNode", label.anchorNodeId());
            features.add(GeoJsonFeature.of(GeoJsonGeometry.point(label.coord()), props));
        }
        return GeoJsonFeatureCollection.of(features);
    }

    /**
     * 링크 선 형상을 만든다. {@code 시작노드 좌표 → 중간 정점 → 끝노드 좌표} 순서.
     * 양 끝 노드 좌표가 없으면 {@code null}(형상 미생성).
     */
    private GeoJsonGeometry buildLineGeometry(NetworkLink link, Map<String, Coord> coordById) {
        Coord start = coordById.get(link.getNode1Id());
        Coord end = coordById.get(link.getNode2Id());
        if (start == null || end == null) {
            return null;
        }
        List<Coord> line = new ArrayList<>(link.getVertices().size() + 2);
        line.add(start);
        line.addAll(link.getVertices());
        line.add(end);
        return GeoJsonGeometry.lineString(line);
    }

    /** 모든 Feature 공통 속성(id/objectType/layer)을 담은 맵을 만든다(이후 도메인 속성이 추가됨). */
    private Map<String, Object> baseProps(String id, String objectTypeName, String layer) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", id);
        props.put("objectType", objectTypeName.toLowerCase(Locale.ROOT));
        props.put("layer", layer);
        return props;
    }
}
