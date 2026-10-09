package com.mindone.editor.inp.network.model;

/**
 * 노드(점) 객체 종류. EPANET 가시 객체(6.4) 중 점 레이어를 구성한다.
 *
 * <p>레이어 키({@link #layer})는 GeoJSON 응답의 레이어명/프론트 토글과 1:1 매핑된다.</p>
 */
public enum NodeType {

    JUNCTION("junctions"),
    RESERVOIR("reservoirs"),
    TANK("tanks"),
    ;

    private final String layer;

    NodeType(String layer) {
        this.layer = layer;
    }

    /** 레이어 키(예: junctions). */
    public String layer() {
        return layer;
    }
}
