package com.mindone.editor.inp.vismapping.repository;

import com.mindone.editor.inp.vismapping.domain.InpVisMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * INP 시각화 매핑 저장소.
 */
public interface InpVisMappingRepository extends JpaRepository<InpVisMapping, Long> {

    /** 특정 INP 파일의 전체 매핑을 매핑 ID 오름차순(등록순)으로 조회한다. */
    List<InpVisMapping> findByInpFileIdOrderByMappingIdAsc(String inpFileId);

    /** 특정 INP 파일에 속한 단일 매핑을 조회한다(파일 소속까지 함께 검증). */
    Optional<InpVisMapping> findByMappingIdAndInpFileId(Long mappingId, String inpFileId);

    /** 같은 INP 파일 안에 같은 junction 의 매핑이 이미 있는지(생성 시 중복 검사). */
    boolean existsByInpFileIdAndJunctionId(String inpFileId, String junctionId);

    /** 같은 INP 파일 안에 같은 junction 의 매핑이 자기 자신을 제외하고 있는지(수정 시 중복 검사). */
    boolean existsByInpFileIdAndJunctionIdAndMappingIdNot(String inpFileId, String junctionId, Long mappingId);
}
