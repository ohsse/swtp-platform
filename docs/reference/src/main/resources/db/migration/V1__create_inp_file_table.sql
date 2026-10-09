-- INP 파일 메타데이터 마스터 테이블
-- 물리 파일은 스토리지에 UUID 파일명({inp_file_id}.{확장자})으로 저장되고,
-- 사용자가 입력한 원본 파일명(orgnl_file_nm)은 표시용으로 이 테이블에 보관한다.
CREATE TABLE inp_file_m (
    inp_file_id    VARCHAR(36)  NOT NULL COMMENT 'INP 파일 ID(UUID, 스토리지 저장 파일명으로 사용)',
    orgnl_file_nm  VARCHAR(255) NOT NULL COMMENT '원본 파일명(사용자 입력값, 표시용)',
    stor_file_nm   VARCHAR(255) NOT NULL COMMENT '저장 파일명(스토리지 실제 파일명 = {uuid}.{확장자})',
    file_xtns      VARCHAR(20)  NOT NULL COMMENT '파일 확장자(업로드 파일 기준, 예: inp)',
    file_sz        BIGINT       NOT NULL COMMENT '파일 크기(byte)',
    file_hash      VARCHAR(64)           COMMENT 'SHA-256 해시(무결성/중복감지, 선택)',
    rgst_dttm      DATETIME(6)  NOT NULL COMMENT '등록일시',
    mdf_dttm       DATETIME(6)  NOT NULL COMMENT '수정일시',
    PRIMARY KEY (inp_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INP 파일 메타데이터';
