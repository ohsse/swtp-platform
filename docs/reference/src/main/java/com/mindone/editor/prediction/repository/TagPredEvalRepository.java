package com.mindone.editor.prediction.repository;

import com.mindone.editor.prediction.domain.PredictionDuration;
import com.mindone.editor.prediction.domain.TagPredEval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 태그 예측 정확도 평가 저장소.
 */
public interface TagPredEvalRepository extends JpaRepository<TagPredEval, Long> {

    /**
     * 태그번호 + 단일 예측시간(target)의 예측구간별 평가행을 조회한다.
     *
     * <p>한 예측시간({@code pred_dttm = target})에 대해 예측구간(M10/M30/H1/H3/H6)별로 최대 1행씩 존재한다
     * (UNIQUE(tag_no, pred_dttm, duration_cd)). 같은 시각의 결과를 예측구간(horizon)별로 비교하기 위한
     * 시점 조회이며, 오차항(abs_err/sq_err/ape/sape)은 행에 이미 계산돼 있으므로 그대로 읽는다.</p>
     *
     * @param tagNo     태그번호
     * @param target    예측시간(예측 대상 시각, 정확히 일치)
     * @param durations 모니터링 대상 예측구간(대상 외 코드가 섞이지 않도록 제한)
     */
    @Query("""
            select p from TagPredEval p
            where p.tagNo = :tagNo
              and p.predDttm = :target
              and p.duration in :durations
            """)
    List<TagPredEval> findByTarget(@Param("tagNo") String tagNo,
                                   @Param("target") LocalDateTime target,
                                   @Param("durations") Collection<PredictionDuration> durations);

    /**
     * 차트용 시계열: 태그번호 + 예측시간 구간의 예측점을 예측구간·예측시간 순으로 조회한다.
     *
     * @param tagNo     태그번호
     * @param from      예측시간 시작(이상)
     * @param to        예측시간 끝(이하)
     * @param durations 모니터링 대상 예측구간(대상 외 코드가 섞이지 않도록 제한)
     */
    @Query("""
            select p from TagPredEval p
            where p.tagNo = :tagNo
              and p.predDttm between :from and :to
              and p.duration in :durations
            order by p.duration asc, p.predDttm asc
            """)
    List<TagPredEval> findSeries(@Param("tagNo") String tagNo,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("durations") Collection<PredictionDuration> durations);
}
