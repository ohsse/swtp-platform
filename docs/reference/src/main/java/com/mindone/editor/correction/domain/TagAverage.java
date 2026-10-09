package com.mindone.editor.correction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 태그 평균값 엔티티(보정 기준 마스터).
 *
 * <p>태그번호별 평균값 1건을 나타낸다. 계측 보정 트리거({@code trg_tag_meas_corr})가
 * 이 평균값을 읽어 {@link TagMeasurement} 의 보정값을 채운다.</p>
 *
 * <p>평균값은 외부에서 산정·적재하는 기준 데이터이므로 이 엔티티는 조회 전용으로 둔다.</p>
 */
@Entity
@Table(name = "tag_avg_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagAverage {

    /** 태그번호 (SCADA 태그, PK). */
    @Id
    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    /** 태그 평균값 (보정 기준). */
    @Column(name = "avg_val", nullable = false)
    private double avgVal;

    /** 등록일시. */
    @Column(name = "reg_dt", nullable = false, updatable = false)
    private LocalDateTime regDt;

    /** 수정일시. */
    @Column(name = "upd_dt")
    private LocalDateTime updDt;
}
