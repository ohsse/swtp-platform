package com.mo.swtp.ems.ctrl.repository;

import java.util.List;

import com.mo.swtp.ems.ctrl.domain.CtrlGrp;
import com.mo.swtp.ems.support.UseYn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제어그룹 조회.
 *
 * <p><b>정렬을 파생 쿼리 메서드로 쓰지 않는다.</b> {@code sort_ord}가 nullable이고
 * 업무 유니크 제약도 없어 같은 값·NULL이 공존할 수 있는데, {@code findAllByOrderBySortOrdAsc()}
 * 형태로는 NULL의 위치도 동순위 타이브레이커도 지정할 수 없다. 그러면 같은 요청이 매번
 * 다른 순서를 반환할 수 있다 — 스캔 순서는 보장되지 않기 때문이다.
 * 그래서 HQL로 {@code nulls last} + PK 타이브레이커까지 못박는다.
 */
public interface CtrlGrpRepository extends JpaRepository<CtrlGrp, String> {

    /** 전체 목록 — 정렬순서 오름차순, 미지정은 뒤로, 동순위는 ID로 확정 */
    @Query("select c from CtrlGrp c order by c.sortOrd asc nulls last, c.ctrlGrpId asc")
    List<CtrlGrp> findAllOrdered();

    /** 사용여부로 거른 목록 — 정렬 규칙은 위와 같다 */
    @Query("select c from CtrlGrp c where c.useYn = :useYn order by c.sortOrd asc nulls last, c.ctrlGrpId asc")
    List<CtrlGrp> findAllByUseYnOrdered(@Param("useYn") UseYn useYn);
}
