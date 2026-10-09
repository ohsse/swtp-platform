package com.mindone.editor.inp.network.geojson;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * GeoJSON FeatureCollection — 한 레이어의 Feature 묶음.
 *
 * @param type     항상 {@code "FeatureCollection"}
 * @param features Feature 목록
 */
@Schema(description = "GeoJSON FeatureCollection(레이어 단위)")
public record GeoJsonFeatureCollection(
        @Schema(description = "컬렉션 타입", example = "FeatureCollection") String type,
        @Schema(description = "Feature 목록") List<GeoJsonFeature> features
) {

    /** FeatureCollection 을 생성한다. */
    public static GeoJsonFeatureCollection of(List<GeoJsonFeature> features) {
        return new GeoJsonFeatureCollection("FeatureCollection", features);
    }
}
