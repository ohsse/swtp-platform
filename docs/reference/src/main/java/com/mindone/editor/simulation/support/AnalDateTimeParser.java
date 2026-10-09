package com.mindone.editor.simulation.support;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 분석일시(analDateTime) 문자열 파서.
 *
 * <p>시뮬레이션 조회 계열 API 가 공통으로 쓰는 {@code yyyy-MM-dd HH:mm}(분 단위) 형식을
 * {@link LocalDateTime}(초=00) 으로 파싱한다. 형식이 잘못되면 {@link CommonErrorCode#INVALID_PARAMETER}.</p>
 */
public final class AnalDateTimeParser {

    /** 분석일시 입력 포맷(yyyy-MM-dd HH:mm). */
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AnalDateTimeParser() {
    }

    /**
     * 분석일시 문자열을 {@link LocalDateTime} 으로 파싱한다.
     *
     * @param analDateTime 분석일시 문자열(형식 {@code yyyy-MM-dd HH:mm})
     * @return 파싱된 일시(초=00)
     * @throws RestApiException 값이 비어 있거나 형식이 잘못되면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    public static LocalDateTime parse(String analDateTime) {
        if (analDateTime == null || analDateTime.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        try {
            return LocalDateTime.parse(analDateTime.trim(), FORMAT);
        } catch (DateTimeParseException e) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
