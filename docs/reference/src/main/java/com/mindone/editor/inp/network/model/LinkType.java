package com.mindone.editor.inp.network.model;

/**
 * 링크(선) 객체 종류. EPANET 가시 객체(6.4) 중 선 레이어를 구성한다.
 *
 * <p>링크는 자체 좌표가 없고 양 끝 노드 좌표(+중간 정점)로 선을 그린다. 레이어 키({@link #layer})는
 * GeoJSON 응답의 레이어명/프론트 토글과 1:1 매핑된다.</p>
 */
public enum LinkType {

    PIPE("pipes"),
    PUMP("pumps"),
    VALVE("valves"),
    ;

    private final String layer;

    LinkType(String layer) {
        this.layer = layer;
    }

    /** 레이어 키(예: pipes). */
    public String layer() {
        return layer;
    }
}
