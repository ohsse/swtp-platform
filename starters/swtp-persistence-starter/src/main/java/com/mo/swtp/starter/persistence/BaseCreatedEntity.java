package com.mo.swtp.starter.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 등록 전용 감사 컬럼 규약 — {@code rgstr_dttm} / {@code rgstr_id}.
 *
 * <p>한 번 적재되면 수정하지 않는 테이블(이력·로그·매핑 테이블 등)이 상속한다.
 * 수정까지 발생하는 테이블은 {@link BaseEntity}를 상속한다.
 *
 * <p>수정 컬럼은 NOT NULL이므로({@link BaseEntity} 참고) 수정하지 않는 테이블에
 * {@code mdf_dttm}/{@code mdf_id}를 형식적으로 붙이면 등록값을 복사하는 것 말고는 채울 값이 없다 —
 * 전 행이 {@code mdf_dttm = rgstr_dttm}인 무의미한 컬럼 두 개가 스키마 전반에 남는다.
 * 그래서 규약 자체를 두 단계로 나눈다: <b>수정 컬럼의 존재 여부가 곧 그 테이블이 수정되는지의 선언이다.</b>
 *
 * <p>값 주입은 Spring Data JPA Auditing이 담당한다 —
 * {@link SwtpJpaAuditingAutoConfiguration}이 JPA가 구성된 앱에서만 조건부로 활성화한다.
 * {@code @EntityListeners}는 상속되므로 이 클래스에서 한 번만 선언한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    /** 등록일시 — insert 시 1회 기록되며 이후 변경되지 않는다(updatable=false) */
    @CreatedDate
    @Column(name = "rgstr_dttm", nullable = false, updatable = false)
    private LocalDateTime rgstrDttm;

    /** 등록ID — {@code AuditorProvider}가 공급한 값. 비인증 컨텍스트에서는 시스템 기본값 */
    @CreatedBy
    @Column(name = "rgstr_id", nullable = false, updatable = false, length = 50)
    private String rgstrId;

    public LocalDateTime getRgstrDttm() {
        return rgstrDttm;
    }

    public String getRgstrId() {
        return rgstrId;
    }
}
