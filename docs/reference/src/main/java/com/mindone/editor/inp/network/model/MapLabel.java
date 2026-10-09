package com.mindone.editor.inp.network.model;

/**
 * 지도 라벨({@code [LABELS]}). 점 좌표에 표시되는 텍스트로, 노드와 성격이 달라 별도 레이어로 다룬다.
 *
 * @param coord        라벨 위치(투영좌표)
 * @param text         라벨 텍스트
 * @param anchorNodeId 라벨이 고정된 노드 ID(선택, 없으면 {@code null})
 */
public record MapLabel(Coord coord, String text, String anchorNodeId) {
}
