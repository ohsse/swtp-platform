package com.mo.swtp.master.prcs.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.starter.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prcs_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prcs extends BaseEntity {
    
    @Id
    @Column(name = "prcs_id", nullable = false, length = ColLength.PRCS_ID)
    private String prcsId;
    
    @Column(name = "prcs_nm", nullable = false, length = ColLength.PRCS_NM)
    private String prcsNm;
    
    @Column(name = "prcs_type_cd", nullable = false, length = ColLength.PRCS_TYPE_CD)
    private String prcsTypeCd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = ColLength.USE_YN)
    private UseYn useYn = UseYn.Y;
    
    @Column(name = "sort_ord")
    private Integer sortOrd;

    public Prcs(String prcsId, String prcsNm, String prcsTypeCd, UseYn useYn, Integer sortOrd) {
        this.prcsId = prcsId;
        this.prcsNm = prcsNm;
        this.prcsTypeCd = prcsTypeCd;
        this.useYn = useYn == null ? UseYn.Y : useYn;
        this.sortOrd = sortOrd;
    }

    public void replace(String prcsNm, String prcsTypeCd, UseYn useYn, Integer sortOrd) {
        if(prcsNm != null) {
            this.prcsNm = prcsNm;
        }

        if(prcsTypeCd != null) {
            this.prcsTypeCd = prcsTypeCd;
        }

        if(useYn != null) {
            this.useYn = useYn;
        }

        if(sortOrd != null) {
            this.sortOrd = sortOrd;
        }
    }
}
