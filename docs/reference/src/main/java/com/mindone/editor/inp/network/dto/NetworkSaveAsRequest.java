package com.mindone.editor.inp.network.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * INP "다른 이름으로 저장" 요청 — 편집된 상세조회 응답 + 새 파일명.
 *
 * <p>상세조회 응답({@link NetworkDetailResponse})의 세 필드(meta/layers/sections)를 그대로 펼치고,
 * 저장할 새 파일명({@link #fileName})만 추가한 형태다. 프론트는 상세조회로 받은 응답을 편집한 뒤 새 이름과
 * 함께 그대로 돌려보내면 된다. 원본 파일은 수정하지 않고 <b>새 INP 파일 레코드 + 물리 파일</b>이 추가된다.</p>
 *
 * @param fileName 저장할 새 파일명({@code .inp} 가 없으면 자동으로 붙는다)
 * @param meta     메타데이터(BACKDROP 경계 등) — 없어도 동작
 * @param layers   가시 객체 레이어별 GeoJSON
 * @param sections 비가시 섹션 데이터(설정은 {@code OPTIONS} 키)
 */
@Schema(description = "INP 다른 이름으로 저장 요청(편집된 상세조회 응답 + 새 파일명)")
public record NetworkSaveAsRequest(
        @Schema(description = "저장할 새 파일명", example = "편집본_관망도.inp") String fileName,
        @Schema(description = "메타데이터(BACKDROP 경계 등)") NetworkMeta meta,
        @Schema(description = "가시 객체 레이어별 GeoJSON") NetworkLayers layers,
        @Schema(description = "비가시 섹션 데이터(설정은 OPTIONS 키)") Map<String, Object> sections
) {

    /** 직렬화에 쓰는 저장 요청({@link NetworkSaveRequest})으로 변환한다(파일명 제외). */
    public NetworkSaveRequest toSaveRequest() {
        return new NetworkSaveRequest(meta, layers, sections);
    }
}
