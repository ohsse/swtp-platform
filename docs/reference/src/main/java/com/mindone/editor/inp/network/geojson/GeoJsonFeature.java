package com.mindone.editor.inp.network.geojson;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * GeoJSON Feature — 하나의 객체(노드/링크/라벨)를 기하 + 속성으로 표현한다.
 *
 * <p>{@code geometry} 는 좌표 누락 등으로 형상을 만들 수 없으면 {@code null} 일 수 있다(속성은 유지).</p>
 *
 * @param type       항상 {@code "Feature"}
 * @param geometry   기하(없으면 {@code null})
 * @param properties 객체 속성(id, 도메인 속성, layer, objectType 등)
 */
@Schema(description = "GeoJSON Feature")
public record GeoJsonFeature(
        @Schema(description = "객체 타입", example = "Feature") String type,
        @Schema(description = "기하(좌표 누락 시 null)") GeoJsonGeometry geometry,
        @Schema(description = "객체 속성") Map<String, Object> properties
) {

    /** Feature 를 생성한다. */
    public static GeoJsonFeature of(GeoJsonGeometry geometry, Map<String, Object> properties) {
        return new GeoJsonFeature("Feature", geometry, properties);
    }
}
