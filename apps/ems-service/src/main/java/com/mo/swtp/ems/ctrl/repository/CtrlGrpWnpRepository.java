package com.mo.swtp.ems.ctrl.repository;

import java.util.Collection;
import java.util.List;

import com.mo.swtp.ems.ctrl.domain.CtrlGrpWnp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제어그룹 수계통지점 편성 조회·삭제.
 *
 * <p>삭제 방식과 두 옵션을 함께 켜는 이유는 제어설비 편성 쪽과 같다 — 파생 삭제 메서드는
 * 영속 컨텍스트에 "삭제 예정" 인스턴스를 남겨 재저장을 UPDATE로 뒤바꿀 수 있다.
 */
public interface CtrlGrpWnpRepository extends JpaRepository<CtrlGrpWnp, String> {

    /** 그룹의 편성 목록 */
    @Query("select c from CtrlGrpWnp c where c.ctrlGrpId = :ctrlGrpId "
            + "order by c.sortOrd asc nulls last, c.wnpId asc")
    List<CtrlGrpWnp> findAllByCtrlGrpIdOrdered(@Param("ctrlGrpId") String ctrlGrpId);

    /** 이 그룹의 기존 편성을 비운다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlGrpWnp c where c.ctrlGrpId = :ctrlGrpId")
    int deleteAllByCtrlGrpId(@Param("ctrlGrpId") String ctrlGrpId);

    /** 요청된 지점을 어느 그룹에 있든 떼어낸다 — 뺏어오기 처리 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlGrpWnp c where c.wnpId in :wnpIds")
    int deleteAllByWnpIdIn(@Param("wnpIds") Collection<String> wnpIds);
}
