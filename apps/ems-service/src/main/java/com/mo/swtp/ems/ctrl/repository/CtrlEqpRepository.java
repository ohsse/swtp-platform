package com.mo.swtp.ems.ctrl.repository;

import java.util.Collection;
import java.util.List;

import com.mo.swtp.ems.ctrl.domain.CtrlEqp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제어설비 편성 조회·삭제.
 *
 * <p><b>파생 삭제 메서드({@code deleteByCtrlGrpId})를 쓰지 않는다.</b> Spring Data는 그것을
 * {@code SELECT → em.remove() 반복}으로 구현하므로 편성 크기만큼 쿼리가 늘고, 제거된 인스턴스가
 * 영속 컨텍스트에 "삭제 예정"으로 남는다.
 *
 * <p><b>다만 이 선택이 정확성에 필수라는 것은 검증되지 않았다.</b> 파생 삭제 메서드로 바꿔도
 * 이 앱의 편성 테스트 14건이 전부 통과한다(실측). 이 경로에서는 assigned-ID라
 * {@code save()}가 {@code merge()}를 타고, 그 {@code merge}가 존재 확인 SELECT를 날리면서
 * 보류 중이던 DELETE가 함께 flush되는 것으로 보인다 — <b>추정이며 실행으로 확인하지 않았다.</b>
 * 여기서 벌크 DELETE를 쓰는 근거는 쿼리 수와 컨텍스트 상태의 단순함이지
 * "이것 없으면 깨진다"가 아니다(문서 03 「함정 기록」).
 *
 * <p><b>{@code flushAutomatically}와 {@code clearAutomatically}를 반드시 함께 켠다.</b>
 * {@code clearAutomatically}만 켜면 같은 트랜잭션의 다른 변경이 flush 없이 detach되어 사라지고,
 * {@code flushAutomatically}만 켜면 1차 캐시가 stale해진다 — <b>하나만 켜면 조용히 유실되거나
 * 조용히 stale해진다.</b>
 *
 * <p>{@code @Modifying update}는 두지 않는다. {@code BaseCreatedEntity}/{@code BaseEntity}의
 * 감사 리스너를 우회하기 때문이다. DELETE는 행이 사라지므로 우회할 리스너가 없어 금지 대상이 아니다.
 */
public interface CtrlEqpRepository extends JpaRepository<CtrlEqp, String> {

    /** 그룹의 편성 목록 — 요청 순서로 매긴 sort_ord 오름차순, 동순위는 ID로 확정 */
    @Query("select c from CtrlEqp c where c.ctrlGrpId = :ctrlGrpId "
            + "order by c.sortOrd asc nulls last, c.eqpId asc")
    List<CtrlEqp> findAllByCtrlGrpIdOrdered(@Param("ctrlGrpId") String ctrlGrpId);

    /** 이 그룹의 기존 편성을 비운다 — replace-all의 "빠지는 것" 처리 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlEqp c where c.ctrlGrpId = :ctrlGrpId")
    int deleteAllByCtrlGrpId(@Param("ctrlGrpId") String ctrlGrpId);

    /**
     * 요청된 설비를 어느 그룹에 있든 떼어낸다 — 뺏어오기(steal) 처리.
     *
     * <p><b>이것이 막는 것은 PK 위반이 아니다.</b> 처음에는 그렇게 적었으나 실측으로 반증됐다 —
     * 이 호출을 빼도 편성 테스트가 전부 통과한다. assigned-ID라 {@code save()}가 {@code merge()}를
     * 타므로, 남의 그룹에 남아 있는 행은 INSERT되는 것이 아니라 <b>조용히 UPDATE된다</b>:
     * {@code ctrl_grp_id}만 바뀌고 {@code rgstr_dttm}·{@code rgstr_id}는 {@code updatable = false}라
     * 옛 그룹에 편성되던 시각에 멈춘다. 예외는 나지 않는다.
     *
     * <p>즉 이 삭제가 지키는 것은 <b>"뺏어온 편성은 새로 만들어진 것"</b>이라는 감사 의미다
     * (문서 03 「결정 6」·「함정 기록」 4).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CtrlEqp c where c.eqpId in :eqpIds")
    int deleteAllByEqpIdIn(@Param("eqpIds") Collection<String> eqpIds);
}
