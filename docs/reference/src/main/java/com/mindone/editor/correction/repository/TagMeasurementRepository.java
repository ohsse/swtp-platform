package com.mindone.editor.correction.repository;

import com.mindone.editor.correction.domain.TagMeasurement;
import com.mindone.editor.correction.domain.TagMeasurementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 태그 계측값/보정 저장소.
 */
public interface TagMeasurementRepository extends JpaRepository<TagMeasurement, TagMeasurementId> {

    /**
     * 보정 완료 + 미알림 계측을 조회한다(프론트 폴링용).
     *
     * <p>보정여부 {@code Y}(트리거가 평균값으로 보정 완료) + 알림여부 {@code N}(아직 토스트 미표시)
     * 인 행을, 오래된 계측부터 계측시간 오름차순으로 반환한다.</p>
     */
    @Query("""
            select m from TagMeasurement m
            where m.corrYn = com.mindone.editor.common.domain.YesOrNo.Y
              and m.notiYn = com.mindone.editor.common.domain.YesOrNo.N
            order by m.measTs asc
            """)
    List<TagMeasurement> findPendingNotifications();
}
