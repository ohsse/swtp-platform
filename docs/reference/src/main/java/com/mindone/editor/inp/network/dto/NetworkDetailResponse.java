package com.mindone.editor.inp.network.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * INP 상세조회 응답 — 메타 + 가시 객체 레이어(GeoJSON) + 비가시 섹션 데이터(설정 포함).
 *
 * @param meta     좌표계/단위/개수/경계/경고 등 메타데이터
 * @param layers   가시 객체(절점/관로 등) 레이어별 GeoJSON FeatureCollection. 각 Feature 의 properties 에는
 *                 EPANET 속성편집기(매뉴얼 6.4)와 동일하게 여러 섹션의 값이 통합되며, <b>키 이름은 6.4 라벨을
 *                 camelCase 로 옮긴 것</b>이다(예: 절점은 elevation/baseDemand/demandPattern 외에 tag/
 *                 demandCategories/emitterCoefficient/initialQuality/sourceQuality, 링크는 startNode/endNode,
 *                 펌프는 pumpCurve/efficiencyCurve/initialStatus, 밸브는 type/fixedStatus 등). 객체 타입별로 6.4
 *                 속성편집기의 전체 필드를 항상 키로 노출하며(전체 스키마 고정), 값이 없으면 {@code null} 이다 —
 *                 프론트가 키 유무 검사 없이 폼을 바인딩할 수 있다. 인라인 주석은 {@code description} 으로 노출된다.
 * @param sections 비가시 섹션(CURVES/PATTERNS/CONTROLS/REPORT/LABELS/TITLE/OPTIONS) 데이터 — 섹션명 → 키-밸류 데이터.
 *                 토큰/원본이 아니라 EPANET 의미 필드로 매핑한다. CURVES=ID별 {@code {id,curveType,description,xyData}},
 *                 PATTERNS=ID별 {@code {id,description,multipliers}}.
 *                 <b>CONTROLS</b>={@code {simple, rule}} — 단순 제어문 라인 목록({@code simple})과 규칙 제어
 *                 {@code {id,content}} 목록({@code rule})을 EPANET Controls Editor 처럼 한 키로 묶는다(별도 RULES 키 없음).
 *                 <b>OPTIONS</b>=설정 섹션(OPTIONS/TIMES/REACTIONS/ENERGY)을 EPANET Options 브라우저 구조
 *                 {@code {hydraulics, quality, reactions, times, energy}} 로 묶은 것
 *                 ({@link com.mindone.editor.inp.network.combiner.OptionsCombiner} 참고).
 *                 객체별 속성 섹션(TAGS/DEMANDS/EMITTERS/QUALITY/SOURCES/MIXING/STATUS)은 여기 없고 위 {@code layers}
 *                 의 객체 properties 로 병합된다(중복 제거).
 *                 변환 규칙은 {@link com.mindone.editor.inp.network.combiner.NonVisualSectionCombiner} 참고.
 */
@Schema(description = "INP 상세조회 응답(메타 + 가시 객체 GeoJSON + 비가시 섹션(설정 OPTIONS 포함))")
public record NetworkDetailResponse(
        @Schema(description = "메타데이터") NetworkMeta meta,
        @Schema(description = "가시 객체 레이어별 GeoJSON") NetworkLayers layers,
        @Schema(description = "비가시 섹션 데이터(섹션명 → 데이터, 설정은 OPTIONS 키)") Map<String, Object> sections
) {
}
