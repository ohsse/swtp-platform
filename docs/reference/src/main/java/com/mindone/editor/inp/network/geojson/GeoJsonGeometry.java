package com.mindone.editor.inp.network.geojson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mindone.editor.inp.network.model.Coord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * GeoJSON Geometry. 점은 {@code Point}, 선은 {@code LineString} 을 사용한다.
 *
 * <p>좌표는 투영좌표를 그대로 담는다(재투영은 프론트 책임). {@code coordinates} 의 형태는 타입에 따라
 * 다르므로 직렬화 유연성을 위해 {@link Object} 로 둔다 — Point: {@code [x, y]}, LineString: {@code [[x, y], ...]}.</p>
 *
 * @param type        기하 타입("Point" 또는 "LineString")
 * @param coordinates 좌표 배열
 */
@Schema(description = "GeoJSON Geometry(Point/LineString)")
public record GeoJsonGeometry(
        @Schema(description = "기하 타입", example = "Point") String type,
        @Schema(description = "좌표(Point: [x,y], LineString: [[x,y],...])") Object coordinates
) {

    /** 점 기하를 생성한다. */
    public static GeoJsonGeometry point(Coord c) {
        return new GeoJsonGeometry("Point", new double[]{c.x(), c.y()});
    }

    /**
     * 선 기하를 생성한다. 좌표가 2개 미만이면 유효한 선이 아니므로 {@code null} 반환(호출측에서 geometry 생략).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static GeoJsonGeometry lineString(List<Coord> coords) {
        if (coords == null || coords.size() < 2) {
            return null;
        }
        double[][] arr = new double[coords.size()][];
        for (int i = 0; i < coords.size(); i++) {
            Coord c = coords.get(i);
            arr[i] = new double[]{c.x(), c.y()};
        }
        return new GeoJsonGeometry("LineString", arr);
    }
}
