package com.mo.swtp.ems.ctrl.repository;

import java.util.Collection;
import java.util.List;

import com.mo.swtp.ems.ctrl.domain.CtrlGrpTag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제어그룹 태그 편성 조회·삭제.
 *
 * <p>삭제 방식과 두 옵션을 함께 켜는 이유는 제어설비 편성 쪽과 같다.
 */
public interface CtrlGrpTagRepository extends JpaRepository<CtrlGrpTag, String> {

    /** 그룹의 편성 목록 */
    @Query("select c from CtrlGrpTag c where c.ctrlGrpId = :ctrlGrpId "
            + "order by c.sortOrd asc nulls last, c.tagSn asc")
    List<CtrlGrpTag> findAllByCtrlGrpIdOrdered(@Param("ctrlGrpId") String ctrlGrpId);

    /** 이 그룹의 기존 편성을 비운다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlGrpTag c where c.ctrlGrpId = :ctrlGrpId")
    int deleteAllByCtrlGrpId(@Param("ctrlGrpId") String ctrlGrpId);

    /** 요청된 태그를 어느 그룹에 있든 떼어낸다 — 뺏어오기 처리 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlGrpTag c where c.tagSn in :tagSns")
    int deleteAllByTagSnIn(@Param("tagSns") Collection<String> tagSns);
}
