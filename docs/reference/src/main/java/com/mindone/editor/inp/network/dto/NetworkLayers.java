package com.mindone.editor.inp.network.dto;

import com.mindone.editor.inp.network.geojson.GeoJsonFeatureCollection;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 레이어별 GeoJSON FeatureCollection 묶음 — 형상 종류로 3개 레이어로 그룹핑한다.
 *
 * <ul>
 *     <li>{@code nodeLayer}(Point): 절점(Junction)/저수지(Reservoir)/탱크(Tank)</li>
 *     <li>{@code linkLayer}(LineString): 관로(Pipe)/펌프(Pump)/밸브(Valve)</li>
 *     <li>{@code labelLayer}(Point): 라벨</li>
 * </ul>
 *
 * <p>각 레이어는 프론트(OpenLayers)의 개별 레이어/토글과 1:1 대응한다. 합쳐진 레이어 안에서도 Feature
 * 속성의 {@code objectType}(junction/reservoir/tank/pipe/pump/valve)·{@code layer}(세부 레이어명)으로
 * 세부 타입을 구분해 스타일링할 수 있다.</p>
 */
@Schema(description = "레이어별 GeoJSON FeatureCollection(노드/링크/라벨)")
public record NetworkLayers(
        @Schema(description = "노드 레이어(절점/저수지/탱크) — Point") GeoJsonFeatureCollection nodeLayer,
        @Schema(description = "링크 레이어(관로/펌프/밸브) — LineString") GeoJsonFeatureCollection linkLayer,
        @Schema(description = "라벨 레이어 — Point") GeoJsonFeatureCollection labelLayer
) {
}
