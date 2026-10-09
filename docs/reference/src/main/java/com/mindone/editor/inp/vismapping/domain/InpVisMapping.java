package com.mindone.editor.inp.vismapping.domain;

import com.mindone.editor.common.domain.BaseEntity;
import com.mindone.editor.common.domain.YesOrNo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * INP 시각화 매핑 엔티티.
 *
 * <p>특정 INP 파일의 한 시각화 지점을 정의한다. 절점(junction) 1개와 관로(pipe) 1개를 INP 객체 ID 로 가리키고,
 * 그 지점의 유량태그번호/압력태그번호/지점명을 함께 보관한다. 한 INP 파일에 여러 지점을 매핑할 수 있으며
 * (1:N), 같은 파일 안에서 같은 junction 의 중복 매핑은 DB UNIQUE 제약으로 차단한다.</p>
 *
 * <p>junction/pipe ID 가 실제 INP 파일에 존재하는지는 검증하지 않고 입력값을 그대로 저장한다(단순 매핑).</p>
 *
 * <p>식별자는 DB 자동증가(IDENTITY)를 쓴다. 등록/수정 일시는 {@link BaseEntity} 가 자동 관리한다.</p>
 */
@Entity
@Table(name = "inp_vis_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InpVisMapping extends BaseEntity {

    /** 매핑 PK (대리키, DB 자동증가). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    /** INP 파일 ID (마스터 FK, 생성 후 변경 불가). */
    @Column(name = "inp_file_id", nullable = false, length = 36, updatable = false)
    private String inpFileId;

    /** 절점(junction) ID (INP 노드 ID). */
    @Column(name = "junction_id", nullable = false)
    private String junctionId;

    /** 관로(pipe) ID (INP 링크 ID). */
    @Column(name = "pipe_id", nullable = false)
    private String pipeId;

    /** 유량태그번호 (SCADA 태그, 선택). */
    @Column(name = "flow_tag_no")
    private String flowTagNo;

    /** 압력태그번호 (SCADA 태그, 선택). */
    @Column(name = "pressure_tag_no")
    private String pressureTagNo;

    /** 지점명 (표시용, 선택). */
    @Column(name = "point_nm")
    private String pointNm;

    /** 정렬순서 */
    @Column(name = "sort_ord")
    private Integer sortOrd;

    /** 표시여부 */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 char(1) 컬럼과 맞도록 JDBC 타입을 CHAR 로 고정한다.
    @Column(name = "disp_yn", length = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private YesOrNo dispYn;


    private InpVisMapping(String inpFileId, String junctionId, String pipeId,
                          String flowTagNo, String pressureTagNo, String pointNm, Integer sortOrd, YesOrNo dispYn) {
        this.inpFileId = inpFileId;
        this.junctionId = junctionId;
        this.pipeId = pipeId;
        this.flowTagNo = flowTagNo;
        this.pressureTagNo = pressureTagNo;
        this.pointNm = pointNm;
        this.sortOrd = sortOrd;
        this.dispYn = dispYn;
    }

    /**
     * 매핑 행을 생성한다.
     *
     * @param inpFileId     대상 INP 파일 ID
     * @param junctionId    절점(junction) ID
     * @param pipeId        관로(pipe) ID
     * @param flowTagNo     유량태그번호(없으면 {@code null})
     * @param pressureTagNo 압력태그번호(없으면 {@code null})
     * @param pointNm       지점명(없으면 {@code null})
     * @param sortOrd       정렬순서
     * @param dispYn        표시여부
     * @return 매핑 엔티티
     */
    public static InpVisMapping create(String inpFileId, String junctionId, String pipeId,
                                       String flowTagNo, String pressureTagNo, String pointNm, Integer sortOrd, YesOrNo dispYn) {
        return new InpVisMapping(inpFileId, junctionId, pipeId, flowTagNo, pressureTagNo, pointNm, sortOrd, dispYn);
    }

    /**
     * 매핑 내용을 수정한다(소속 INP 파일은 변경하지 않음).
     *
     * <p>영속 상태의 엔티티에 호출하면 변경 감지(dirty checking)로 UPDATE 되며 {@code mdf_dttm} 도 갱신된다.</p>
     *
     * @param junctionId    절점(junction) ID
     * @param pipeId        관로(pipe) ID
     * @param flowTagNo     유량태그번호(없으면 {@code null})
     * @param pressureTagNo 압력태그번호(없으면 {@code null})
     * @param pointNm       지점명(없으면 {@code null})
     * @param sortOrd       정렬순서
     * @param dispYn        표시여부
     */
    public void update(String junctionId, String pipeId,
                       String flowTagNo, String pressureTagNo, String pointNm, Integer sortOrd, YesOrNo dispYn) {
        this.junctionId = junctionId;
        this.pipeId = pipeId;
        this.flowTagNo = flowTagNo;
        this.pressureTagNo = pressureTagNo;
        this.pointNm = pointNm;
        this.sortOrd = sortOrd;
        this.dispYn = dispYn;
    }
}
