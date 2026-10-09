package com.mo.swtp.master.eqp.repository;

import com.mo.swtp.master.support.UseYn;

import com.mo.swtp.master.eqp.domain.Eqp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EqpRepository extends JpaRepository<Eqp, String> {

    /** ■■■■■■■■■■■■■■■■   전체 설비 목록 조회   ■■■■■■■■■■■■■■■■*/
    List<Eqp> findAllByOrderBySortOrdAsc();

    /** ■■■■■■■■■■■■■■■■  사용 중인 전체 설비목록 조회   ■■■■■■■■■■■■■■■■*/
    List<Eqp> findAllByUseYnOrderBySortOrdAsc(UseYn useYn);

    /**
     * ■■■■■■■■■■■■■■■■   특정 설비타입 목록 조회   ■■■■■■■■■■■■■■■■
     *
     * <p>반환 타입이 {@code Optional<List<Eqp>>}였다가 {@code List<Eqp>}가 됐다 — 컬렉션 파생 쿼리는
     * 결과가 없어도 {@code Optional.empty()}가 아니라 빈 리스트를 담은 {@code Optional}을 준다.
     * 즉 그 {@code orElseThrow}는 한 번도 발화하지 않는 죽은 코드였고, 남겨 두면
     * "이 엔드포인트가 404를 낼 수 있다"는 거짓이 스펙에 실린다(05 「결정 2」).
     */
    List<Eqp> findAllByEqpTypeCdOrderBySortOrdAsc(String eqpTypeCd);

    /** ■■■■■■■■■■■■■■■■   특정 시설 설비목록 조회   ■■■■■■■■■■■■■■■■*/
    List<Eqp> findAllByFcltIdOrderBySortOrdAsc(String fcltId);
}
