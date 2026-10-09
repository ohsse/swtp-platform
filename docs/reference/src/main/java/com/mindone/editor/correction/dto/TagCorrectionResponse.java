package com.mindone.editor.correction.dto;

import com.mindone.editor.correction.domain.TagMeasurement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 태그 보정 알림 응답.
 *
 * <p>보정 완료 + 미알림 계측 1건을 표현한다. {@code message} 는 프론트가 그대로 토스트로 띄울 수 있게
 * 서버에서 조립한 문구다(프론트에서 원본 필드로 직접 조립해도 된다).</p>
 *
 * @param measTs   계측시간(복합키)
 * @param tagNo    태그번호(복합키)
 * @param rawVal   원본값(입력된 헌팅값)
 * @param corrVal  보정값(평균보간 결과)
 * @param message  토스트용 안내 문구
 */
@Schema(description = "태그 보정 알림 응답")
public record TagCorrectionResponse(
        @Schema(description = "계측시간") LocalDateTime measTs,
        @Schema(description = "태그번호") String tagNo,
        @Schema(description = "원본값(헌팅값)") double rawVal,
        @Schema(description = "보정값(평균보간 결과)") Double corrVal,
        @Schema(description = "토스트용 안내 문구") String message
) {

    /**
     * 계측 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param m 계측 엔티티(보정 완료 상태)
     */
    public static TagCorrectionResponse from(TagMeasurement m) {
        String message = "태그번호 %s 가 헌팅값 %s 에서 평균보간을 사용해서 %s 값으로 보정되었습니다."
                .formatted(m.getTagNo(), m.getRawVal(), m.getCorrVal());
        return new TagCorrectionResponse(
                m.getMeasTs(),
                m.getTagNo(),
                m.getRawVal(),
                m.getCorrVal(),
                message
        );
    }
}
