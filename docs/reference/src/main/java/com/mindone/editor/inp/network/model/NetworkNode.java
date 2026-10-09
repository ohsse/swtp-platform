package com.mindone.editor.inp.network.model;

import lombok.Getter;

import java.util.Map;

/**
 * 네트워크 노드(점 객체) — Junction/Reservoir/Tank 공통 표현.
 *
 * <p>객체별 속성(고도/수요/수두 등)은 {@link #properties} 에 컬럼명을 키로 담는다(레이어/타입별로 키가 다름).
 * 좌표({@link #coord})는 {@code [COORDINATES]} 섹션과 결합하는 단계에서 채워지며, 누락될 수 있다(nullable).</p>
 */
@Getter
public class NetworkNode {

    /** 노드 ID(한글 가능). */
    private final String id;

    /** 노드 종류. */
    private final NodeType type;

    /** 객체 속성(컬럼명 → 값). 등장 순서 유지. */
    private final Map<String, Object> properties;

    /** 좌표(투영좌표). {@code [COORDINATES]} 누락 시 {@code null}. */
    private Coord coord;

    public NetworkNode(String id, NodeType type, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.properties = properties;
    }

    /** 좌표를 결합한다. */
    public void setCoord(Coord coord) {
        this.coord = coord;
    }

    /** 좌표 보유 여부. */
    public boolean hasCoord() {
        return coord != null;
    }
}
