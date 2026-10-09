package com.mo.swtp.master.tag.domain;


import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.starter.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tag_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {
    
    @Id
    @Column(name = "tag_sn", length = ColLength.TAG_SN)
    private String tagSn;
    
    @Column(name = "tag_type_cd" , length = ColLength.TAG_TYPE_CD, nullable = false)
    private String tagTypeCd;
    
    @Column(name = "use_yn", length = ColLength.USE_YN, nullable = false)
    @Enumerated(EnumType.STRING)
    private UseYn useYn = UseYn.Y;

    public Tag(String tagSn, String tagTypeCd, UseYn useYn) {
        this.tagSn = tagSn;
        this.tagTypeCd = tagTypeCd;
        this.useYn = useYn == null ? UseYn.Y : useYn;
    }
    
    public void replace(String tagTypeCd, UseYn useYn) {
        if(tagTypeCd != null) {
            this.tagTypeCd = tagTypeCd;
        }
        
        if(useYn != null) {
            this.useYn = useYn;
        }
    }
    
    
}
