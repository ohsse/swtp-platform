package com.mindone.editor.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 엔티티 공통 감사(audit) 컬럼 베이스.
 *
 * <p>모든 엔티티가 공유하는 등록/수정 일시를 제공한다. 로그인(인증) 기능이 없는
 * 독립 애플리케이션이므로 등록자/수정자 식별 컬럼은 두지 않고 일시만 관리한다.</p>
 *
 * <p>일시 값은 Hibernate 의 {@link CreationTimestamp}/{@link UpdateTimestamp} 가
 * INSERT/UPDATE 시점에 자동으로 채운다. 별도의 JPA Auditing 설정은 필요하지 않다.</p>
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    /** 등록일시 (INSERT 시 자동 기록, 이후 변경 불가). */
    @CreationTimestamp
    @Column(name = "rgst_dttm", updatable = false, nullable = false)
    private LocalDateTime rgstDttm;

    /** 수정일시 (INSERT/UPDATE 시 자동 갱신). */
    @UpdateTimestamp
    @Column(name = "mdf_dttm", nullable = false)
    private LocalDateTime mdfDttm;
}
