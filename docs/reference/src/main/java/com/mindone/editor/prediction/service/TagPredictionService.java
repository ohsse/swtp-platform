package com.mindone.editor.prediction.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.dto.TagPredValueResponse;
import com.mindone.editor.prediction.repository.TagPredValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 태그 예측값 조회 서비스.
 *
 * <p>외부 예측 모듈이 공유 DB 에 기록한 태그 예측값을 조회한다(BE 는 쓰기 없이 조회 전용).
 * 태그번호 기준으로 조회하며, 예측시간 구간·예측구간을 선택 필터로 좁힐 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
public class TagPredictionService {

    private final TagPredValueRepository tagPredValueRepository;

    /**
     * 태그번호 기준으로 예측값을 조회한다.
     *
     * <p>예측시간 오름차순, 같은 예측시간 안에서는 예측구간(짧은 것 → 긴 것) 순으로 정렬해 반환한다.
     * 특정 예측시간 1건의 5종 예측구간을 묶어 받으려면 {@code predFrom}/{@code predTo} 에 같은 시각을 준다.</p>
     *
     * @param tagNo    태그번호(필수)
     * @param predFrom 예측시간 시작(이상, 널이면 미적용)
     * @param predTo   예측시간 끝(이하, 널이면 미적용)
     * @param duration 예측구간(널이면 전체)
     * @return 예측값 목록(없으면 빈 목록)
     * @throws RestApiException 태그번호가 비어 있으면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    @Transactional(readOnly = true)
    public List<TagPredValueResponse> search(String tagNo,
                                             LocalDateTime predFrom,
                                             LocalDateTime predTo,
                                             PredictionDuration duration) {
        if (tagNo == null || tagNo.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        return tagPredValueRepository.search(tagNo.trim(), predFrom, predTo, duration, PredictionDuration.MONITORED)
                .stream()
                .map(TagPredValueResponse::from)
                .toList();
    }
}
