package com.mindone.editor.correction.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@link TagMeasurement} 복합키 클래스.
 *
 * <p>계측시간({@code measTs}) + 태그번호({@code tagNo})가 한 계측을 유일하게 식별한다.
 * JPA {@code @IdClass} 규약상 기본 생성자·{@code equals}/{@code hashCode} 를 갖고,
 * 필드명·타입이 엔티티의 {@code @Id} 필드와 일치해야 한다.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TagMeasurementId implements Serializable {

    /** 계측시간. */
    private LocalDateTime measTs;

    /** 태그번호. */
    private String tagNo;
}
