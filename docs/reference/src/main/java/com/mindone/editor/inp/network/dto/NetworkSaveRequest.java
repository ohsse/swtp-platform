package com.mindone.editor.inp.network.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * INP 저장(쓰기) 요청 — 상세조회 응답({@link NetworkDetailResponse})을 프론트가 편집해 그대로 돌려보내는 구조.
 *
 * <p>필드 구성은 상세조회 응답과 동일하다(편집 후 왕복). {@link com.mindone.editor.inp.network.compose.InpComposer}
 * 가 이를 역변환해 전 섹션을 복원하고, {@link com.mindone.editor.inp.network.writer.InpWriter} 가 INP 텍스트로
 * 직렬화한다.</p>
 *
 * <p><b>주의</b>: 상세조회 응답은 가독성을 위한 손실 투영이라(파일 헤더 주석·컬럼 정렬·숫자 표기 등 비의미
 * 정보는 제거됨) 원본 바이트와 완전히 동일한 파일을 재생성하지는 못한다. 다만 의미 정보(객체/속성/섹션/설정)는
 * 무손실로 왕복하므로, 재파싱하면 동일한 네트워크가 복원된다.</p>
 *
 * @param meta     메타데이터(좌표 경계 등) — {@code backdropbounds} 복원에 사용. 없어도 동작한다(BACKDROP 생략).
 * @param layers   가시 객체 레이어별 GeoJSON(절점/관로/라벨) — 좌표/정점/속성 복원의 단일 출처
 * @param sections 비가시 섹션 데이터(TITLE/PATTERNS/CURVES/CONTROLS/REPORT/LABELS/OPTIONS) — CONTROLS 는
 *                 {@code {simple, rule}}, 설정은 {@code OPTIONS} 키에 {@code {hydraulics, quality, reactions,
 *                 times, energy}} 구조로 담긴다(상세조회 응답과 동일)
 */
@Schema(description = "INP 저장(쓰기) 요청 — 편집된 상세조회 응답 구조")
public record NetworkSaveRequest(
        @Schema(description = "메타데이터(BACKDROP 경계 등)") NetworkMeta meta,
        @Schema(description = "가시 객체 레이어별 GeoJSON") NetworkLayers layers,
        @Schema(description = "비가시 섹션 데이터(설정은 OPTIONS 키)") Map<String, Object> sections
) {
}
