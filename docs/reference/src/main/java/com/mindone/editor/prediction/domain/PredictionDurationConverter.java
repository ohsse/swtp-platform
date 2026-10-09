package com.mindone.editor.prediction.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link PredictionDuration} ↔ DB {@code duration_cd} 코드값 변환기.
 *
 * <p>DB 에는 외부 예측 모듈(파이썬)이 쓰는 코드값({@code 10M}/{@code 30M}/{@code 1H}/{@code 3H}/{@code 6H})이
 * 저장되는데, 이는 enum 이름(M10/M30/H1/H3/H6)과 형식이 달라 {@code @Enumerated(STRING)} 으로는 매핑할 수 없다
 * (그 방식은 {@code enum.name()} 기준). 따라서 코드값 기준으로 직접 매핑한다.</p>
 *
 * <p>{@code autoApply = true} 로 모든 {@link PredictionDuration} 속성에 자동 적용된다.
 * 읽기 시 모니터링 대상 외 코드({@code 20M} 등)가 들어오면 {@link PredictionDuration#fromCode} 가 {@code null}
 * 을 돌려주지만, 리포지토리 조회가 대상 코드만 걸러 읽으므로 정상 경로에서는 도달하지 않는다.</p>
 */
@Converter(autoApply = true)
public class PredictionDurationConverter implements AttributeConverter<PredictionDuration, String> {

    @Override
    public String convertToDatabaseColumn(PredictionDuration duration) {
        return (duration == null) ? null : duration.getCode();
    }

    @Override
    public PredictionDuration convertToEntityAttribute(String code) {
        return PredictionDuration.fromCode(code);
    }
}
