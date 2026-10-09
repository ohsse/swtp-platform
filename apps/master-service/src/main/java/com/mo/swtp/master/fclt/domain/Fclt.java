package com.mo.swtp.master.fclt.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.starter.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fclt_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fclt extends BaseEntity {
    
    @Id
    @Column(name = "fclt_id" , nullable = false, length = ColLength.FCLT_ID)
    private String fcltId;
    
    @Column(name = "fclt_nm" , nullable = false, length = ColLength.FCLT_NM)
    private String fcltNm;
    
    @Column(name = "fclt_type_cd", nullable = false , length = ColLength.FCLT_TYPE_CD)
    private String fcltTypeCd;
    
    @Column(name = "sort_ord")
    private Integer sortOrd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = ColLength.USE_YN)
    private UseYn useYn = UseYn.Y;

    public Fclt(String fcltId, String fcltNm, String fcltTypeCd, Integer sortOrd, UseYn useYn) {
        this.fcltId = fcltId;
        this.fcltNm = fcltNm;
        this.fcltTypeCd = fcltTypeCd;
        this.sortOrd = sortOrd;
        this.useYn = useYn == null ? UseYn.Y : useYn;
    }

    public void replace(String fcltNm, String fcltTypeCd, Integer sortOrd, UseYn useYn) {
        if(fcltNm != null) {
            this.fcltNm = fcltNm;
        }

        if(fcltTypeCd != null) {
            this.fcltTypeCd = fcltTypeCd;
        }

        if(sortOrd != null) {
            this.sortOrd = sortOrd;
        }

        if(useYn != null) {
            this.useYn = useYn;
        }
    }
    
}
