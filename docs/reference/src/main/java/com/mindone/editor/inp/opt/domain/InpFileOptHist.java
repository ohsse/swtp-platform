package com.mindone.editor.inp.opt.domain;


import com.mindone.editor.inp.opt.dto.InpAnalMappingSnap;
import com.mindone.editor.inp.opt.dto.ResultSnap;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * INP 파일 최적화 이력 엔티티.
 */
@Entity
@Table(name = "inp_file_opt_h")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InpFileOptHist {

    /** 이력 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hist_id")
    private Long histId;

    /** INP파일 ID */
    @Column(name = "inp_file_id", nullable = false, length = 36, updatable = false)
    private String inpFileId;

    /** 시작일시 */
    @Column(name = "strt_dttm")
    private LocalDateTime strtDttm;

    /** 종료일시 */
    @Column(name = "end_dttm")
    private LocalDateTime endDttm;

    /** 총세대수 */
    @Column(name = "tot_gener_count")
    private Integer totGenerCount;

    /** 진행세대수 */
    @Column(name = "impl_gener_count")
    private Integer implGenerCount;

    /** 진행상태코드 */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 varchar(30) 컬럼과 맞도록 JDBC 타입을 VARCHAR 로 고정한다.
    @Column(name = "status_cd", length = 30)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private OptimizeStatus statusCd;

    /** 최적화이전개정번호 */
    @Column(name = "prev_rev_no")
    private Integer prevRevNo;

    /** 최적화후개정번호 */
    @Column(name = "rev_no")
    private Integer revNo;

    /** 설정스냅샷 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_snap")
    private List<InpAnalMappingSnap> optionSnap = new ArrayList<>();

    /** 최적화이전결과 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prev_result_snap")
    private List<ResultSnap> prevResultSnap = new ArrayList<>();

    /** 최적화이후결과 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snap")
    private List<ResultSnap> resultSnap = new ArrayList<>();

    private InpFileOptHist (String inpFileId, List<InpAnalMappingSnap> optionSnap, Integer prevRevNo) {
        this.inpFileId = inpFileId;
        this.optionSnap = optionSnap;
        this.prevRevNo = prevRevNo;
        this.strtDttm = LocalDateTime.now();
        this.statusCd = OptimizeStatus.READY;
    }

    public static InpFileOptHist create (
            String inpFileId, List<InpAnalMappingSnap> optionSnap, Integer prevRevNo
    ) {
        return new InpFileOptHist(inpFileId, optionSnap, prevRevNo);
    }

    /** 최적화 요청 전달 실패 등으로 이력을 오류 상태로 변경한다. */
    public void markError () {
        this.statusCd = OptimizeStatus.ERROR;
        this.endDttm = LocalDateTime.now();
    }
}
