package com.mo.swtp.ems.wnp.repository;

import java.util.List;

import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.ems.wnp.domain.Wnp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 수계통지점 조회.
 *
 * <p>정렬을 파생 쿼리 메서드로 쓰지 않는다 — {@code sort_ord}가 nullable이고 업무 유니크가 없어
 * NULL의 위치와 동순위 타이브레이커를 명시해야 순서가 결정적이 된다.
 * (제어그룹 쪽 리포지토리도 같은 이유로 같은 형태다. 도메인 간 타입 참조를 만들지 않으려고
 * {@code @link}로 잇지 않는다 — 주석의 링크도 도메인을 떼어낼 때 함께 끊어진다.)
 */
public interface WnpRepository extends JpaRepository<Wnp, String> {

    /** 전체 목록 — 정렬순서 오름차순, 미지정은 뒤로, 동순위는 ID로 확정 */
    @Query("select w from Wnp w order by w.sortOrd asc nulls last, w.wnpId asc")
    List<Wnp> findAllOrdered();

    /** 사용여부로 거른 목록 — 정렬 규칙은 위와 같다 */
    @Query("select w from Wnp w where w.useYn = :useYn order by w.sortOrd asc nulls last, w.wnpId asc")
    List<Wnp> findAllByUseYnOrdered(@Param("useYn") UseYn useYn);
}
