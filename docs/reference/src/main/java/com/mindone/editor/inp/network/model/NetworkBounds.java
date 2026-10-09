package com.mindone.editor.inp.network.model;

import org.locationtech.jts.geom.Envelope;

/**
 * 네트워크 좌표 경계(투영좌표). 프론트의 초기 지도 범위(fit/extent) 계산에 쓰인다.
 *
 * @param minX 최소 X
 * @param minY 최소 Y
 * @param maxX 최대 X
 * @param maxY 최대 Y
 */
public record NetworkBounds(double minX, double minY, double maxX, double maxY) {

    /**
     * JTS {@link Envelope} 로부터 경계를 생성한다. 비어 있으면 {@code null}.
     *
     * @param envelope 좌표를 누적한 Envelope
     * @return 경계 또는 {@code null}(좌표가 하나도 없을 때)
     */
    public static NetworkBounds from(Envelope envelope) {
        if (envelope == null || envelope.isNull()) {
            return null;
        }
        return new NetworkBounds(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY());
    }
}
