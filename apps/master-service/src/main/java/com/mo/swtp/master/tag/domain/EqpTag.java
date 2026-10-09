package com.mo.swtp.master.tag.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.starter.persistence.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "eqp_tag_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EqpTag extends BaseCreatedEntity {
    
    @Id
    @Column(name = "tag_sn", length = ColLength.TAG_SN, nullable = false)
    private String tagSn;
    
    @Column(name = "eqp_id", nullable = false , length = ColLength.EQP_ID)
    private String eqpId;
    
    
}
