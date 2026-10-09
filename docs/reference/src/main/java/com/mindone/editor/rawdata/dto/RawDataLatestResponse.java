package com.mindone.editor.rawdata.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 실측 태그값(TB_RAWDATA) 최신 1건 응답.
 *
 * <p>값({@code value})은 원본 그대로 문자열로 반환한다(TB_RAWDATA.VALUE 가 varchar). 숫자 가공이 필요하면
 * 호출 측에서 변환한다.</p>
 *
 * @param tagNo   태그번호(SCADA 태그)
 * @param ts      계측 일시
 * @param value   계측값(원본 문자열)
 * @param quality 품질 코드
 */
@Schema(description = "실측 태그값 최신 1건 응답")
public record RawDataLatestResponse(
        @Schema(description = "태그번호(SCADA 태그)") String tagNo,
        @Schema(description = "계측 일시") LocalDateTime ts,
        @Schema(description = "계측값(원본 문자열)", example = "20719.5") String value,
        @Schema(description = "품질 코드") String quality
) {
}
