package com.mindone.editor.prediction.repository;

import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.domain.TagPredValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 태그 예측값 저장소.
 */
public interface TagPredValueRepository extends JpaRepository<TagPredValue, Long> {

    /**
     * 태그번호 기준으로 예측값을 조회한다. 예측시간 구간·예측구간은 선택 필터다(널이면 미적용).
     *
     * <p>예측시간 오름차순, 같은 예측시간 안에서는 예측구간(짧은 것 → 긴 것) 순으로 정렬한다.
     * 한 예측시간에 대한 5종 예측구간을 한눈에 묶어 받을 수 있다.</p>
     *
     * @param tagNo     태그번호(필수)
     * @param predFrom  예측시간 시작(이상, 널이면 미적용)
     * @param predTo    예측시간 끝(이하, 널이면 미적용)
     * @param duration  예측구간(널이면 미적용)
     * @param durations 모니터링 대상 예측구간(대상 외 코드가 섞이지 않도록 제한)
     */
    @Query("""
            select p from TagPredValue p
            where p.tagNo = :tagNo
              and (:predFrom is null or p.predDttm >= :predFrom)
              and (:predTo   is null or p.predDttm <= :predTo)
              and (:duration is null or p.duration  = :duration)
              and p.duration in :durations
            order by p.predDttm asc, p.duration asc
            """)
    List<TagPredValue> search(@Param("tagNo") String tagNo,
                              @Param("predFrom") LocalDateTime predFrom,
                              @Param("predTo") LocalDateTime predTo,
                              @Param("duration") PredictionDuration duration,
                              @Param("durations") Collection<PredictionDuration> durations);
}
