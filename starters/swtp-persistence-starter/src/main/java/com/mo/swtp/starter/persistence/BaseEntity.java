package com.mo.swtp.starter.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 등록 + 수정 감사 컬럼 규약 — {@code rgstr_dttm}/{@code rgstr_id} + {@code mdf_dttm}/{@code mdf_id}.
 *
 * <p>수정이 발생하는 테이블이 이 클래스를 상속한다. 등록만 하고 수정하지 않는 테이블은
 * {@link BaseCreatedEntity}를 상속하며, 수정 컬럼을 아예 두지 않는다.
 *
 * <p><b>수정 컬럼은 NOT NULL이다.</b> "컬럼이 있으면 값이 반드시 있다"가 규약이고,
 * "컬럼은 있는데 값이 NULL"인 상태는 존재하지 않는다. {@code @EnableJpaAuditing}의
 * {@code modifyOnCreate} 기본값(true)에 따라 등록 시점에 등록값과 동일하게 채워진 뒤
 * 이후 update마다 갱신된다.
 *
 * <p>따라서 "한 번도 수정되지 않음"은 {@code mdf_dttm = rgstr_dttm}으로 판정한다.
 *
 * <p>수정 컬럼을 채우는 주체는 {@code AuditingEntityListener}이므로 <b>벌크 UPDATE를 쓰지 않는다</b> —
 * JPQL {@code update} 문이나 네이티브 SQL은 리스너를 우회해 수정 컬럼을 그대로 두고,
 * NOT NULL 제약에 걸리거나(insert 경로) 이력이 옛 값에 멈춘다(update 경로).
 * 변경은 반드시 엔티티를 로드해 필드를 바꾸는 방식으로 표현한다.
 */
@MappedSuperclass
public abstract class BaseEntity extends BaseCreatedEntity {

    /** 수정일시 — 등록 시점에 {@code rgstr_dttm}과 같은 값으로 채워지고, 이후 update마다 갱신된다 */
    @LastModifiedDate
    @Column(name = "mdf_dttm", nullable = false)
    private LocalDateTime mdfDttm;

    /** 수정ID — 등록 시점에 {@code rgstr_id}와 같은 값으로 채워지고, 이후 update마다 갱신된다 */
    @LastModifiedBy
    @Column(name = "mdf_id", nullable = false, length = 50)
    private String mdfId;

    public LocalDateTime getMdfDttm() {
        return mdfDttm;
    }

    public String getMdfId() {
        return mdfId;
    }
}
