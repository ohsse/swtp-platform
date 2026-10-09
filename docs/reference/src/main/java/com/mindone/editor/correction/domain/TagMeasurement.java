package com.mindone.editor.correction.domain;

import com.mindone.editor.common.domain.YesOrNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 태그 계측값/보정 엔티티.
 *
 * <p>계측시간 + 태그번호로 식별되는 계측 1건을 나타낸다. 원본값({@code rawVal})으로 헌팅값이
 * INSERT 되면 DB 트리거({@code trg_tag_meas_corr})가 {@link TagAverage} 의 평균값을 읽어
 * 보정값({@code corrVal})에 채우고 보정여부({@code corrYn})를 {@code Y} 로 표시한다.</p>
 *
 * <p>프론트는 "보정 완료 + 미알림"({@code corrYn=Y AND notiYn=N}) 행을 폴링해 토스트를 띄운 뒤
 * 알림여부({@code notiYn})를 {@code Y} 로 갱신한다. 원본값 적재는 외부에서 이뤄지므로
 * 이 엔티티는 조회 + 알림여부 갱신만 담당한다.</p>
 */
@Entity
@Table(name = "tag_meas_l")
@IdClass(TagMeasurementId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagMeasurement {

    /** 계측시간 (ms 단위, 복합키). */
    @Id
    @Column(name = "meas_ts", nullable = false)
    private LocalDateTime measTs;

    /** 태그번호 (SCADA 태그, 복합키). */
    @Id
    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    /** 원본값 (입력된 헌팅값). */
    @Column(name = "raw_val", nullable = false)
    private double rawVal;

    /** 보정값 (평균보간 결과, 트리거가 채움). */
    @Column(name = "corr_val")
    private Double corrVal;

    /** 보정여부 (Y/N). */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 char(1) 컬럼과 맞도록 JDBC 타입을 CHAR 로 고정한다.
    @Column(name = "corr_yn", nullable = false, length = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private YesOrNo corrYn;

    /** 토스트 알림 처리여부 (Y/N). */
    @Column(name = "noti_yn", nullable = false, length = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private YesOrNo notiYn;

    /**
     * 토스트 알림 완료로 표시한다.
     *
     * <p>영속 상태의 엔티티에 호출하면 변경 감지(dirty checking)로 {@code noti_yn} 이 {@code Y} 로 UPDATE 된다.</p>
     */
    public void markNotified() {
        this.notiYn = YesOrNo.Y;
    }
}
