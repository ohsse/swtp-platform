package com.mindone.editor.inp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * INP 파일 리비전(이력) 엔티티.
 *
 * <p>마스터({@link InpFile})는 {@code curr_rev_no} 로 "현재 적용 리비전"만 가리키고, 저장 파일명·크기·
 * 작업구분 등 리비전별 정보는 이 테이블에 append-only 로 쌓인다. 한 번 기록된 리비전 행은 변경되지 않으므로
 * 수정일시 컬럼을 두지 않는다(등록일시만).</p>
 *
 * <p>식별자는 DB 자동증가(IDENTITY)를 쓴다. 마스터(UUID 선생성)와 달리, 리비전은 물리 파일명을
 * {@code inpFileId + rev_no} 로 결정하므로 PK 를 미리 알 필요가 없다.</p>
 */
@Entity
@Table(name = "inp_file_rev_h")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InpFileRevision {

    /** 리비전 PK (대리키, DB 자동증가). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rev_id")
    private Long revId;

    /** INP 파일 ID (마스터 FK). */
    @Column(name = "inp_file_id", nullable = false, length = 36, updatable = false)
    private String inpFileId;

    /** 리비전 번호 (0부터 1씩 증가, max+1 채번). */
    @Column(name = "rev_no", nullable = false, updatable = false)
    private int revNo;

    /** 저장 파일명 (스토리지 실제 파일명, 권장 규약 {uuid}_r{rev_no}.{확장자}). */
    @Column(name = "stor_file_nm", nullable = false)
    private String storFileNm;

    /** 파일 크기 (byte). */
    @Column(name = "file_sz", nullable = false)
    private long fileSz;

    /** SHA-256 해시 (무결성/중복감지, 선택). */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /** 작업 구분 (ORIGIN/EDIT/OPTIMIZE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 20)
    private RevisionWorkType workType;

    /** 등록일시 (INSERT 시 자동 기록, 이후 변경 불가). */
    @CreationTimestamp
    @Column(name = "rgst_dttm", updatable = false, nullable = false)
    private LocalDateTime rgstDttm;

    private InpFileRevision(String inpFileId, int revNo, String storFileNm,
                           long fileSz, String fileHash, RevisionWorkType workType) {
        this.inpFileId = inpFileId;
        this.revNo = revNo;
        this.storFileNm = storFileNm;
        this.fileSz = fileSz;
        this.fileHash = fileHash;
        this.workType = workType;
    }

    /**
     * 리비전 이력 행을 생성한다.
     *
     * @param inpFileId  마스터 INP 파일 ID
     * @param revNo      리비전 번호
     * @param storFileNm 저장 파일명
     * @param fileSz     파일 크기(byte)
     * @param fileHash   파일 해시(없으면 {@code null})
     * @param workType   작업 구분(ORIGIN/EDIT/OPTIMIZE)
     * @return 리비전 엔티티
     */
    public static InpFileRevision create(String inpFileId, int revNo, String storFileNm,
                                         long fileSz, String fileHash, RevisionWorkType workType) {
        return new InpFileRevision(inpFileId, revNo, storFileNm, fileSz, fileHash, workType);
    }
}
