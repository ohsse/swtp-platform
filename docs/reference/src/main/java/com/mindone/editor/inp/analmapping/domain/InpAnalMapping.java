package com.mindone.editor.inp.analmapping.domain;

import com.mindone.editor.common.domain.BaseEntity;
import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.opt.domain.DataType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * INP 분석 매핑 엔티티.
 *
 * <p>특정 INP 파일의 한 분석 대상 측정 지점을 정의한다. 노드(node) 1개를 INP 객체 ID 로 가리키고,
 * 그 지점을 측정하는 SCADA 태그번호와 데이터유형({@link DataType} FLOW/PRESSURE)을 함께 보관한다.
 * 한 INP 파일에 여러 매핑을 둘 수 있으며(1:N), 같은 파일 안에서 같은 node + 데이터유형의 중복 매핑은
 * DB UNIQUE 제약으로 차단한다.</p>
 *
 * <p>node ID 가 실제 INP 파일에 존재하는지는 검증하지 않고 입력값을 그대로 저장한다(단순 매핑).</p>
 *
 * <p>식별자는 DB 자동증가(IDENTITY)를 쓴다. 등록/수정 일시는 {@link BaseEntity} 가 자동 관리한다.</p>
 */
@Entity
@Table(name = "inp_anal_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InpAnalMapping extends BaseEntity {

    /** 매핑 PK (대리키, DB 자동증가). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    /** INP 파일 ID (마스터 FK, 생성 후 변경 불가). */
    @Column(name = "inp_file_id", nullable = false, length = 36, updatable = false)
    private String inpFileId;

    /** 노드(node) ID (INP 객체 ID). */
    @Column(name = "node_id", nullable = false)
    private String nodeId;

    /** 태그번호 (SCADA 태그). */
    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    /** 데이터유형 (FLOW/PRESSURE). */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 varchar 컬럼과 맞도록 JDBC 타입을 VARCHAR 로 고정한다.
    @Column(name = "data_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private DataType dataType;

    /** 분석여부 */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 char(1) 컬럼과 맞도록 JDBC 타입을 CHAR 로 고정한다.
    @Column(name = "anal_yn", length = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private YesOrNo analYn;

    private InpAnalMapping(String inpFileId, String nodeId, String tagNo, DataType dataType, YesOrNo analYn) {
        this.inpFileId = inpFileId;
        this.nodeId = nodeId;
        this.tagNo = tagNo;
        this.dataType = dataType;
        this.analYn = analYn;
    }

    /**
     * 분석 매핑 행을 생성한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param nodeId    노드(node) ID
     * @param tagNo     태그번호(SCADA 태그)
     * @param dataType  데이터유형(FLOW/PRESSURE)
     * @param analYn    분석여부
     * @return 분석 매핑 엔티티
     */
    public static InpAnalMapping create(String inpFileId, String nodeId, String tagNo, DataType dataType, YesOrNo analYn) {
        return new InpAnalMapping(inpFileId, nodeId, tagNo, dataType, analYn);
    }

    /**
     * 매핑 내용을 수정한다(소속 INP 파일은 변경하지 않음).
     *
     * <p>영속 상태의 엔티티에 호출하면 변경 감지(dirty checking)로 UPDATE 되며 {@code mdf_dttm} 도 갱신된다.</p>
     *
     * @param nodeId   노드(node) ID
     * @param tagNo    태그번호(SCADA 태그)
     * @param dataType 데이터유형(FLOW/PRESSURE)
     * @param analYn   분석여부
     */
    public void update(String nodeId, String tagNo, DataType dataType, YesOrNo analYn) {
        this.nodeId = nodeId;
        this.tagNo = tagNo;
        this.dataType = dataType;
        this.analYn = analYn;
    }
}
