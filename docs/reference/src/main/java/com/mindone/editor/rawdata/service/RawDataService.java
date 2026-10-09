package com.mindone.editor.rawdata.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.rawdata.dto.RawDataLatestResponse;
import com.mindone.editor.rawdata.repository.RawDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 실측 태그값 조회 서비스.
 *
 * <p>외부 SCADA 수집 모듈이 공유 EMS DB({@code TB_RAWDATA})에 적재한 실측값을 조회한다(BE 는 쓰기 없이 조회 전용).</p>
 */
@Service
@RequiredArgsConstructor
public class RawDataService {

    private final RawDataRepository rawDataRepository;

    /**
     * 특정 태그의 오늘(자정~현재) 계측분 중 가장 마지막(최신) 실측값 1건을 조회한다.
     *
     * @param tagNo 태그번호(필수)
     * @return 오늘의 최신 실측값(오늘 계측값이 없으면 {@code null})
     * @throws RestApiException 태그번호가 비어 있으면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    @Transactional(readOnly = true)
    public RawDataLatestResponse latestToday(String tagNo) {
        if (tagNo == null || tagNo.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.atTime(LocalTime.MAX);
        return rawDataRepository.findLatest(tagNo.trim(), from, to)
                .orElse(null);
    }
}
