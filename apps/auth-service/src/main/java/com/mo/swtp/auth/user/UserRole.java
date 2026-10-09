package com.mo.swtp.auth.user;

import com.mo.swtp.starter.persistence.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자-역할 매핑.
 *
 * <p><b>등록 전용 테이블</b>이라 {@link BaseCreatedEntity}를 상속한다 —
 * 역할 회수는 이 행을 수정하는 게 아니라 삭제로 표현하므로 수정 컬럼이 존재할 이유가 없다.
 *
 * <p>{@code @ManyToMany}로 매핑하지 않은 이유: 조인 테이블은 하이버네이트가 직접 INSERT/DELETE하므로
 * 감사 컬럼({@code rgstr_dttm}/{@code rgstr_id})이 채워지지 않아 NOT NULL 위반이 난다.
 * 감사 컬럼이 있는 매핑 테이블은 반드시 엔티티로 드러내야 한다.
 */
@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_role_id")
    private Long userRoleId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "role_id", nullable = false, length = 50)
    private String roleId;

    public UserRole(String userId, String roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }
}
