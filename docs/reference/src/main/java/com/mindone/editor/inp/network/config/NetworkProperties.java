package com.mindone.editor.inp.network.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네트워크 상세조회 설정 프로퍼티({@code editor.network.*}).
 *
 * <p>INP 좌표는 한국 투영좌표(미터)로, 정확한 EPSG 는 파일/지자체마다 다를 수 있어 추측하지 않는다.
 * 운영 환경에서 실제 좌표계를 {@code coordinate-crs}(예: {@code EPSG:5186})로 지정하면 응답 메타에
 * 그대로 노출되어 프론트(proj4)가 재투영에 사용한다. 미지정 시 메타에는 {@code UNKNOWN} 으로 표기된다.</p>
 *
 * @param coordinateCrs 좌표계 식별자(예: EPSG:5186). 비어 있으면 미지정.
 */
@ConfigurationProperties(prefix = "editor.network")
public record NetworkProperties(String coordinateCrs) {

    /** 좌표계 표기값(미지정/공백이면 {@code UNKNOWN}). */
    public String crsOrUnknown() {
        return (coordinateCrs == null || coordinateCrs.isBlank()) ? "UNKNOWN" : coordinateCrs;
    }
}
