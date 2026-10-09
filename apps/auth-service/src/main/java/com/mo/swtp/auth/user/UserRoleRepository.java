package com.mo.swtp.auth.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** 토큰의 {@code roles} 클레임에 실을 역할 코드 목록 */
    @Query("select ur.roleId from UserRole ur where ur.userId = :userId order by ur.roleId")
    List<String> findRoleIdsByUserId(String userId);

}
