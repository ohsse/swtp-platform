package com.mindone.editor.inp.network.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 네트워크 링크(선 객체) — Pipe/Pump/Valve 공통 표현.
 *
 * <p>링크는 자체 좌표가 없다. 선 형상은 {@code 시작노드 → 중간정점들 → 끝노드} 좌표로 구성되며,
 * 중간정점({@link #vertices})은 {@code [VERTICES]} 섹션과 결합하는 단계에서 순서대로 채워진다.
 * 객체별 속성(직경/상태/밸브타입 등)은 {@link #properties} 에 컬럼명을 키로 담는다.</p>
 */
@Getter
public class NetworkLink {

    /** 링크 ID(한글 가능). */
    private final String id;

    /** 링크 종류. */
    private final LinkType type;

    /** 시작 노드 ID. */
    private final String node1Id;

    /** 끝 노드 ID. */
    private final String node2Id;

    /** 객체 속성(컬럼명 → 값). 등장 순서 유지. */
    private final Map<String, Object> properties;

    /** 중간 정점(투영좌표, 순서 유지). {@code [VERTICES]} 없으면 빈 목록. */
    private final List<Coord> vertices = new ArrayList<>();

    public NetworkLink(String id, LinkType type, String node1Id, String node2Id, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.node1Id = node1Id;
        this.node2Id = node2Id;
        this.properties = properties;
    }

    /** 중간 정점을 순서대로 추가한다. */
    public void addVertex(Coord vertex) {
        vertices.add(vertex);
    }
}
