package com.mindone.editor.inp.repository;

import com.mindone.editor.inp.domain.InpFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * INP 파일 메타데이터 저장소.
 */
public interface InpFileRepository extends JpaRepository<InpFile, String> {

    /**
     * 전체 INP 파일을 등록일시 내림차순(최신순)으로, 각 파일의 <b>현재 적용 리비전</b>과 조인해 조회한다.
     *
     * <p>마스터({@code inp_file_m})의 현재 적용 리비전 번호({@code curr_rev_no})와 일치하는
     * 리비전 이력({@code inp_file_rev_h})을 ID·리비전번호로 조인한다. 각 행은
     * {@code Object[]{InpFile, InpFileRevision}} 쌍으로, 저장 파일명·크기·작업구분(work_type)은
     * 현재 적용 리비전 기준 값이다. 목록 조회의 N+1 을 피하기 위해 단일 조인 쿼리로 가져온다.</p>
     *
     * @return {@code Object[]{InpFile, InpFileRevision}} 목록 (없으면 빈 목록)
     */
    @Query("""
            select m, r
            from InpFile m
            join InpFileRevision r on r.inpFileId = m.inpFileId and r.revNo = m.currRevNo
            order by m.rgstDttm desc
            """)
    List<Object[]> findAllWithCurrentRevision();

    /**
     * 단일 INP 파일을 <b>현재 적용 리비전</b>과 조인해 조회한다.
     *
     * <p>마스터({@code inp_file_m})의 현재 적용 리비전 번호({@code curr_rev_no})와 일치하는 리비전 이력
     * ({@code inp_file_rev_h})을 LEFT JOIN 한다. PK 조건이라 결과는 0건 또는 1건이며, 행은
     * {@code Object[]{InpFile, InpFileRevision}} 쌍이다.</p>
     *
     * <ul>
     *   <li>마스터가 없으면 → 빈 목록(파일 없음)</li>
     *   <li>마스터는 있으나 현재 리비전 행이 없으면 → {@code row[1]} 이 {@code null}(리비전 무결성 결손)</li>
     * </ul>
     *
     * <p>두 결손을 서로 다른 오류로 구분하기 위해 INNER 가 아닌 LEFT JOIN 을 쓴다. 반환 타입을
     * {@code Optional<Object[]>} 가 아닌 {@code List<Object[]>} 로 둔 이유: Spring Data 는 컴포넌트가
     * 배열인 단건 반환({@code Optional<Object[]>})을 "행들을 배열에 담으라"로 오해해 결과를 한 번 더
     * 감싸므로({@code Object[]{Object[]{m,r}}}), 다중 행과 동일한 {@code List<Object[]>} 튜플 매핑을 쓴다.</p>
     *
     * @param inpFileId INP 파일 ID
     * @return {@code Object[]{InpFile, InpFileRevision}} 0~1건 목록 (마스터 없으면 빈 목록)
     */
    @Query("""
            select m, r
            from InpFile m
            left join InpFileRevision r on r.inpFileId = m.inpFileId and r.revNo = m.currRevNo
            where m.inpFileId = :inpFileId
            """)
    List<Object[]> findWithCurrentRevisionById(@Param("inpFileId") String inpFileId);

    /**
     * 여러 INP 파일을 각자의 <b>현재 적용 리비전</b>과 조인해 조회한다(다중 다운로드용, N+1 회피).
     *
     * <p>마스터({@code curr_rev_no})와 일치하는 리비전 이력을 INNER JOIN 하므로, 현재 리비전 행이 있는
     * 마스터만 결과에 포함된다(현재 리비전 행은 라이프사이클상 항상 존재하는 불변식). 각 행은
     * {@code Object[]{InpFile, InpFileRevision}} 쌍이며 정렬은 보장하지 않는다(호출 측에서 요청 순서로 재정렬).</p>
     *
     * @param inpFileIds 조회할 INP 파일 ID 목록
     * @return {@code Object[]{InpFile, InpFileRevision}} 목록 (없으면 빈 목록)
     */
    @Query("""
            select m, r
            from InpFile m
            join InpFileRevision r on r.inpFileId = m.inpFileId and r.revNo = m.currRevNo
            where m.inpFileId in :inpFileIds
            """)
    List<Object[]> findAllWithCurrentRevisionByIdIn(@Param("inpFileIds") Collection<String> inpFileIds);
}
