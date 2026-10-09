package com.mindone.editor.inp.repository;

import com.mindone.editor.inp.domain.InpFileRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * INP 파일 리비전(이력) 저장소.
 */
public interface InpFileRevisionRepository extends JpaRepository<InpFileRevision, Long> {

    /** 특정 파일의 특정 리비전을 조회한다. */
    Optional<InpFileRevision> findByInpFileIdAndRevNo(String inpFileId, int revNo);

    /** 특정 파일의 전체 리비전을 리비전 번호 내림차순(최신 우선)으로 조회한다. */
    List<InpFileRevision> findByInpFileIdOrderByRevNoDesc(String inpFileId);

    /** 특정 파일의 전체 리비전을 조회한다(삭제 시 물리 파일명 수집용). */
    List<InpFileRevision> findByInpFileId(String inpFileId);

    /** 특정 파일의 현재 최대 리비전 번호. 리비전이 없으면 {@code null}. (다음 번호 = max+1) */
    @Query("select max(r.revNo) from InpFileRevision r where r.inpFileId = :inpFileId")
    Integer findMaxRevNo(@Param("inpFileId") String inpFileId);

    /** 특정 파일의 전체 리비전 행을 일괄 삭제한다(마스터 삭제 cascade). */
    void deleteByInpFileId(String inpFileId);
}
