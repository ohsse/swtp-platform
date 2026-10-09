-- INP 분석 매핑 테이블
-- 특정 INP 파일의 분석 대상 측정 지점을 정의한다: node 1개 + SCADA 태그번호 + 데이터유형(FLOW/PRESSURE).
-- 한 INP 파일에 여러 매핑을 둘 수 있다(1:N). 같은 파일 안에서 같은 node + 데이터유형 중복은 UNIQUE 로 차단한다.
--
-- [삭제 정책]
--   매핑은 물리 파일이 없는 순수 메타데이터이므로, 마스터(inp_file_m) 삭제 시 매핑 행도 함께 사라지도록
--   FK 에 ON DELETE CASCADE 를 건다.
--
-- 공유 DB(덤프/운영)에는 이미 테이블이 존재할 수 있으므로 CREATE TABLE IF NOT EXISTS 로 멱등하게 만든다.
CREATE TABLE IF NOT EXISTS inp_anal_mapping (
    mapping_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '매핑 PK(대리키)',
    inp_file_id  VARCHAR(36)  NOT NULL COMMENT 'INP 파일 ID(마스터 inp_file_m FK)',
    node_id      VARCHAR(255) NOT NULL COMMENT '노드 ID(INP 객체 ID)',
    tag_no       VARCHAR(255) NOT NULL COMMENT '태그번호(SCADA 태그)',
    data_type    VARCHAR(20)  NOT NULL COMMENT '데이터유형(FLOW/PRESSURE)',
    rgst_dttm    DATETIME(6)  NOT NULL COMMENT '등록일시',
    mdf_dttm     DATETIME(6)  NOT NULL COMMENT '수정일시',
    PRIMARY KEY (mapping_id),
    UNIQUE KEY uq_anal_mapping_file_node_type (inp_file_id, node_id, data_type),
    KEY idx_anal_mapping_file_id (inp_file_id),
    CONSTRAINT fk_anal_mapping_inp_file FOREIGN KEY (inp_file_id)
        REFERENCES inp_file_m (inp_file_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INP 분석 매핑';
