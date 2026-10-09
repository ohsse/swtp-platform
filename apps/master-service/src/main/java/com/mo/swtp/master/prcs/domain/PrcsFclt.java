package com.mo.swtp.master.prcs.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.starter.persistence.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prcs_fclt_r")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrcsFclt extends BaseCreatedEntity {
    
    @Id
    @Column(name = "rel_id" , nullable = false, length = 36)
    private String relId;
    
    @Column(name = "prcs_id", nullable = false, length = ColLength.PRCS_ID)
    private String prcsId;
    
    @Column(name = "fclt_id", nullable = false, length = ColLength.FCLT_ID)
    private String fcltId;
    
}
