package com.mindone.editor.inp.network.model;

import com.mindone.editor.inp.network.parser.ParsedInp;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * INP 한 파일을 표현하는 네트워크 도메인 모델.
 *
 * <p>가시 객체(노드/링크/라벨)는 좌표가 결합된 타입드 형태로, 비가시 객체/설정 전체는
 * {@link #parsed}(무손실 섹션 구조)로 함께 보관한다. {@link com.mindone.editor.inp.network.combiner.GeoJsonCombiner}
 * 가 이 모델을 레이어별 GeoJSON 으로 변환한다.</p>
 */
@Getter
@Builder
public class NetworkModel {

    /** 노드(Junction/Reservoir/Tank) 전체. */
    private final List<NetworkNode> nodes;

    /** 링크(Pipe/Pump/Valve) 전체. */
    private final List<NetworkLink> links;

    /** 지도 라벨. */
    private final List<MapLabel> labels;

    /** 핵심 해석 옵션(단위/손실식/수요모델 등). */
    private final NetworkOptions options;

    /** 좌표/라벨로부터 계산한 경계(없으면 {@code null}). */
    private final NetworkBounds bounds;

    /** {@code [BACKDROP] DIMENSIONS} 가 명시한 경계(없으면 {@code null}). */
    private final NetworkBounds backdropBounds;

    /** 파싱/결합 중 발견한 경고(좌표 누락 노드/링크 등). */
    private final List<String> warnings;

    /** 전 섹션 무손실 파싱 결과(비가시 객체 포함 원본 접근용). */
    private final ParsedInp parsed;
}
