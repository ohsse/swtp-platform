package com.mindone.editor.correction.service;

import com.mindone.editor.correction.domain.TagMeasurement;
import com.mindone.editor.correction.domain.TagMeasurementId;
import com.mindone.editor.correction.dto.TagCorrectionAckRequest;
import com.mindone.editor.correction.dto.TagCorrectionResponse;
import com.mindone.editor.correction.repository.TagMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 태그 보정 알림 서비스.
 *
 * <p>DB 트리거가 평균값으로 보정한 계측 중 아직 알리지 않은 건을 조회하고(폴링),
 * 프론트가 토스트를 띄운 뒤 보낸 확인(ack)으로 알림여부를 갱신한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TagCorrectionService {

    private final TagMeasurementRepository tagMeasurementRepository;

    /**
     * 보정 완료 + 미알림 계측을 조회한다(프론트 폴링용).
     *
     * @return 토스트로 띄울 보정 알림 목록(없으면 빈 목록)
     */
    @Transactional(readOnly = true)
    public List<TagCorrectionResponse> findPending() {
        return tagMeasurementRepository.findPendingNotifications()
                .stream()
                .map(TagCorrectionResponse::from)
                .toList();
    }

    /**
     * 프론트가 토스트로 띄운 계측들을 알림 완료로 표시한다.
     *
     * <p>요청 키 목록 중 존재하는 행만 알림여부를 {@code Y} 로 갱신한다(존재하지 않는 키는 무시).
     * 변경 감지(dirty checking)로 UPDATE 되며, 이미 {@code Y} 인 행은 그대로 둔다.</p>
     *
     * @param request 알림 완료로 표시할 계측 복합키 목록(널·빈 목록이면 아무 작업도 하지 않음)
     * @return 실제로 알림 완료로 갱신된 건수
     */
    @Transactional
    public int markNotified(TagCorrectionAckRequest request) {
        if (request == null || request.keys() == null || request.keys().isEmpty()) {
            return 0;
        }
        List<TagMeasurementId> ids = request.keys().stream()
                .map(k -> new TagMeasurementId(k.measTs(), k.tagNo()))
                .toList();
        List<TagMeasurement> targets = tagMeasurementRepository.findAllById(ids);
        targets.forEach(TagMeasurement::markNotified);
        return targets.size();
    }
}
