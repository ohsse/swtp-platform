package com.mindone.editor.inp.network.model;

/**
 * 2차원 좌표(투영좌표, 미터). GeoJSON 의 {@code [x, y]} 와 1:1 대응한다.
 *
 * @param x X 좌표(동서)
 * @param y Y 좌표(남북)
 */
public record Coord(double x, double y) {
}
