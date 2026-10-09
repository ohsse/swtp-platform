package com.mo.swtp.master.eqp.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.starter.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "eqp_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Eqp extends BaseEntity {
    
    @Id
    @Column(name = "eqp_id", length = ColLength.EQP_ID)
    private String eqpId;
    
    @Column(name = "fclt_id", nullable = false, length = ColLength.FCLT_ID)
    private String fcltId;
    
    @Column(name = "eqp_nm", nullable = false, length = ColLength.EQP_NM)
    private String eqpNm;
    
    @Column(name = "eqp_type_cd", nullable = false, length = ColLength.EQP_TYPE_CD)
    private String eqpTypeCd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false , length = ColLength.USE_YN)
    private UseYn useYn = UseYn.Y; //Enum 이런식으로 만들면 되는지 확인 default Value
    
    @Column(name = "sort_ord")
    private Integer sortOrd; //이거 Integer 맞는지 확인 필요

    public Eqp(String eqpId, String fcltId, String eqpNm, String eqpTypeCd, UseYn useYn, Integer sortOrd) {
        this.eqpId = eqpId;
        this.fcltId = fcltId;
        this.eqpNm = eqpNm;
        this.eqpTypeCd = eqpTypeCd;
        this.useYn = useYn == null ? UseYn.Y : useYn;
        this.sortOrd = sortOrd;
    }

    public void replace(String fcltId, String eqpNm, String eqpTypeCd, UseYn useYn, Integer sortOrd) {
        if(fcltId != null) {
            this.fcltId = fcltId;
        }

        if(eqpNm != null) {
            this.eqpNm = eqpNm;
        }

        if(eqpTypeCd != null) {
            this.eqpTypeCd = eqpTypeCd;
        }

        if(useYn != null) {
            this.useYn = useYn;
        }

        if(sortOrd != null) {
            this.sortOrd = sortOrd;
        }
    }
}
