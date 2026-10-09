package com.mindone.editor.inp.domain;

import com.mindone.editor.common.domain.BaseEntity;
import com.mindone.editor.common.domain.YesOrNo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * INP 파일 메타데이터 마스터 엔티티.
 *
 * <p>마스터는 파일 단위 불변 정보(원본 파일명·확장자)와 "현재 적용 리비전 포인터"({@link #currRevNo})만
 * 보관한다. 실제 저장 파일명·크기 등 리비전별 정보는 {@link InpFileRevision} 이력 테이블에 쌓인다.</p>
 *
 * <p>식별자(UUID)는 DB 가 아니라 애플리케이션에서 생성한다. 파일을 스토리지에 쓰기
 * 전에 저장 파일명을 확정해야 하기 때문이다.</p>
 */
@Entity
@Table(name = "inp_file_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InpFile extends BaseEntity implements Persistable<String> {

    /** INP 파일 ID (UUID, 저장 파일명의 기준값). */
    @Id
    @Column(name = "inp_file_id", length = 36, updatable = false)
    private String inpFileId;

    /** 원본 파일명 (사용자 입력값, 표시용). */
    @Column(name = "orgnl_file_nm", nullable = false)
    private String orgnlFileNm;

    /** 파일 확장자 (업로드 파일 기준, 예: inp). */
    @Column(name = "file_xtns", nullable = false, length = 20)
    private String fileXtns;

    /**
     * 현재 적용 리비전 번호 (이력 테이블의 어느 리비전을 현재 상태로 볼지 가리키는 포인터).
     *
     * <p>최초 생성 시 0. 덮어쓰기/최적화로 새 리비전이 추가되면 그 리비전으로 이동하고,
     * 롤백 시 기존 리비전 번호로 되돌린다(새 리비전을 만들지 않음).</p>
     */
    @Column(name = "curr_rev_no", nullable = false)
    private int currRevNo;

    /**
     * 모니터링 여부 (이 파일을 모니터링 대상으로 삼을지).
     *
     * <p>최초 생성 시 {@link YesOrNo#N}(모니터링 안 함). 사용자가 토글하면 Y/N 으로 바뀐다.</p>
     */
    // @Enumerated(STRING) 은 MariaDB 에서 네이티브 ENUM 컬럼으로 매핑되므로,
    // 실제 char(1) 컬럼과 맞도록 JDBC 타입을 CHAR 로 고정한다.
    @Column(name = "mntr_yn", length = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private YesOrNo monitoringYn;

    /**
     * 신규 엔티티 여부 (영속 컬럼 아님).
     *
     * <p>UUID 를 애플리케이션에서 직접 할당하므로, Spring Data 가 "식별자가 존재한다"는
     * 이유만으로 기존 엔티티(merge 대상)로 오판하지 않도록 {@link Persistable} 로 명시한다.
     * 최초 저장 시 INSERT(persist) 를 타도록 보장한다.</p>
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    private InpFile(String orgnlFileNm, String fileXtns) {
        this.inpFileId = UUID.randomUUID().toString();
        this.orgnlFileNm = orgnlFileNm;
        this.fileXtns = fileXtns;
        this.currRevNo = 0; // 최초 리비전(rev0)을 가리킨다.
        this.monitoringYn = YesOrNo.N; // 기본값: 모니터링 안 함.
    }

    /**
     * INP 파일 마스터를 생성한다(현재 적용 리비전 0).
     *
     * <p>리비전 0 이력 행과 물리 파일은 호출 측(서비스)이 함께 생성한다.</p>
     *
     * @param orgnlFileNm 사용자가 입력한 원본 파일명 (표시용)
     * @param fileXtns    업로드 파일의 확장자
     * @return 식별자가 확정된 마스터 엔티티
     */
    public static InpFile create(String orgnlFileNm, String fileXtns) {
        return new InpFile(orgnlFileNm, fileXtns);
    }

    /**
     * 지정한 리비전 번호의 저장 파일명을 생성한다({uuid}_r{revNo}.{확장자}).
     *
     * <p>새 리비전 물리 파일을 쓸 때 사용하는 권장 명명 규약이다. 확장자가 없으면 접미사만 붙인다.</p>
     *
     * @param revNo 리비전 번호
     * @return 저장 파일명
     */
    public String storFileNmForRev(int revNo) {
        String base = inpFileId + "_r" + revNo;
        return (fileXtns == null || fileXtns.isBlank()) ? base : base + "." + fileXtns;
    }

    /**
     * 현재 적용 리비전 포인터를 변경한다(덮어쓰기/롤백).
     *
     * <p>영속 상태의 엔티티에 호출하면 변경 감지(dirty checking)로 UPDATE 되며,
     * {@code mdf_dttm}(수정일시)도 자동 갱신된다.</p>
     *
     * @param revNo 새 현재 리비전 번호
     */
    public void applyRevision(int revNo) {
        this.currRevNo = revNo;
    }

    /**
     * 모니터링 여부를 변경한다.
     *
     * <p>영속 상태의 엔티티에 호출하면 변경 감지(dirty checking)로 UPDATE 되며,
     * {@code mdf_dttm}(수정일시)도 자동 갱신된다.</p>
     *
     * @param monitoringYn 모니터링 대상 여부(Y/N)
     */
    public void changeMonitoring(YesOrNo monitoringYn) {
        this.monitoringYn = monitoringYn;
    }

    @Override
    public String getId() {
        return inpFileId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** INSERT/조회 직후 신규 플래그를 해제한다. */
    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
