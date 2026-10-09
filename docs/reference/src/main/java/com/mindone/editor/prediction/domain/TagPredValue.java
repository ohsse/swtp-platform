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
 * 태그 예측값 엔티티.
 *
 * <p>SCADA 태그번호별 예측값 1건을 나타낸다. 한 (태그번호, 예측시간) 조합에 대해 예측 구간
 * ({@link PredictionDuration} M10/M30/H1/H3/H6) 5종의 예측값이 각각 한 건씩 존재할 수 있으며,
 * (태그번호, 예측시간, 예측구간)이 한 예측값을 유일하게 식별한다(DB UNIQUE 제약).</p>
 *
 * <p>행은 외부 예측 모듈이 공유 DB 에 직접 기록한다(BE 는 쓰기 엔드포인트 없이 조회만 한다).
 * 따라서 이 엔티티는 조회 전용이며 등록/수정 메서드를 두지 않는다.</p>
 */
@Entity
@Table(name = "tag_pred_l")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagPredValue {

    /** 예측값 PK (대리키, DB 자동증가). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pred_id")
    private Long predId;

    /** 태그번호 (SCADA 태그). */
    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    /** 예측시간 (예측 대상 시각). */
    @Column(name = "pred_dttm", nullable = false)
    private LocalDateTime predDttm;

    /** 생성시간 (예측 생성 시각). */
    @Column(name = "crt_dttm", nullable = false)
    private LocalDateTime crtDttm;

    /** 예측 구간 (DB 코드 10M/30M/1H/3H/6H ↔ enum M10/M30/H1/H3/H6). */
    // DB duration_cd 에는 파이썬 코드값(10M 등)이 저장되어 enum 이름(M10)과 다르므로
    // @Enumerated(STRING) 대신 코드 기준 컨버터로 매핑한다.
    @Column(name = "duration_cd", nullable = false, length = 10)
    @Convert(converter = PredictionDurationConverter.class)
    private PredictionDuration duration;

    /** 예측값. */
    @Column(name = "pred_value", nullable = false)
    private double predValue;
}
