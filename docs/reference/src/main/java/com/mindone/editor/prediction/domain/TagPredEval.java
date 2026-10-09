package com.mindone.editor.prediction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 태그 예측 정확도 평가 엔티티 (예측-실측 페어, 예측점 1행).
 *
 * <p>{@code tag_pred_eval_l} 테이블 1행 = 한 (태그번호, 예측시간, 예측구간)의 예측값(p)과 실측 윈도 평균(a),
 * 그리고 그로부터 파생된 오차항(abs_err/sq_err/ape/sape)을 담는다. 파생항은 DB STORED 생성컬럼이라
 * 읽기 전용이며, 적재는 MariaDB EVENT 가 수행한다(BE 는 조회만).</p>
 *
 * <p>화면 정확도 통계는 이 행들을 {@code GROUP BY duration} 으로 합산해 산출한다
 * (RMSE/MAE/MAPE/sMAPE 가 예측점별 항의 평균 형태라 가산적).</p>
 */
@Entity
@Table(name = "tag_pred_eval_l")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagPredEval {

    /** 평가 PK (대리키, DB 자동증가). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eval_id")
    private Long evalId;

    /** 태그번호 (SCADA 태그 = TB_RAWDATA.TAGNAME). */
    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    /** 예측시간 (예측 대상 시각). */
    @Column(name = "pred_dttm", nullable = false)
    private LocalDateTime predDttm;

    /** 예측 구간 (DB 코드 10M/30M/1H/3H/6H ↔ enum M10/M30/H1/H3/H6). */
    @Column(name = "duration_cd", nullable = false, length = 10)
    @Convert(converter = PredictionDurationConverter.class)
    private PredictionDuration duration;

    /** 예측 생성시간 (= 실측 윈도 시작). */
    @Column(name = "crt_dttm", nullable = false)
    private LocalDateTime crtDttm;

    /** 예측값 (p). */
    @Column(name = "pred_value", nullable = false)
    private double predValue;

    /** 실측 윈도 평균 (a). 윈도 내 유효 raw 가 없으면 null. */
    @Column(name = "actual_avg")
    private Double actualAvg;

    /** 실측 윈도 내 표본 수. */
    @Column(name = "sample_cnt")
    private Integer sampleCnt;

    // ===== 파생 오차항 (DB STORED 생성컬럼, 읽기 전용) =====

    /** 절대오차 |a-p| (MAE용). */
    @Column(name = "abs_err", insertable = false, updatable = false)
    private Double absErr;

    /** 제곱오차 (a-p)^2 (RMSE용). */
    @Column(name = "sq_err", insertable = false, updatable = false)
    private Double sqErr;

    /** 절대백분율오차 |(a-p)/a|*100 (MAPE용, a=0이면 null). */
    @Column(name = "ape", insertable = false, updatable = false)
    private Double ape;

    /** 대칭절대백분율오차 (sMAPE용). */
    @Column(name = "sape", insertable = false, updatable = false)
    private Double sape;

    /** 집계(평가) 수행 시각. */
    @Column(name = "eval_dttm")
    private LocalDateTime evalDttm;
}
