package com.mindone.editor.inp.opt.repository;

import com.mindone.editor.inp.opt.domain.InpFileOptHist;
import com.mindone.editor.inp.opt.domain.OptimizeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InpOptHistRepository extends JpaRepository<InpFileOptHist, Long> {

    /**
     * 최적화 이력을 조건부로 조회한다(최신순).
     *
     * <p>각 파라미터는 {@code null} 이면 해당 조건을 생략한다.
     * (예: {@code statusCd} 만 전달하면 모든 파일에서 해당 상태의 이력을 조회)</p>
     *
     * @param inpFileId INP 파일 ID(없으면 전체)
     * @param statusCd  진행상태코드(없으면 전체)
     * @return 조건에 맞는 최적화 이력 목록(이력 ID 내림차순)
     */
    @Query("""
            select h
            from InpFileOptHist h
            where (:inpFileId is null or h.inpFileId = :inpFileId)
              and (:statusCd is null or h.statusCd = :statusCd)
            order by h.histId desc
            """)
    List<InpFileOptHist> findAllByCondition(@Param("inpFileId") String inpFileId,
                                            @Param("statusCd") OptimizeStatus statusCd);
}
