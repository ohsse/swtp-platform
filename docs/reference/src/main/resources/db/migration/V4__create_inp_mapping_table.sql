-- INP 측정 지점 매핑 테이블
-- 특정 INP 파일의 측정 지점을 정의한다: junction(절점) 1개 + pipe(관로) 1개 + 유량태그번호 + 압력태그번호 + 지점명.
-- 한 INP 파일에 여러 지점을 매핑할 수 있다(1:N). 같은 파일 안에서 같은 junction 의 중복 매핑은 UNIQUE 로 차단한다.
--
-- [삭제 정책]
--   매핑은 물리 파일이 없는 순수 메타데이터이므로, 마스터(inp_file_m) 삭제 시 매핑 행도 함께 사라지도록
--   FK 에 ON DELETE CASCADE 를 건다. (리비전 테이블은 물리 파일 수집이 필요해 애플리케이션이 직접 삭제하지만,
--    매핑은 DB 캐스케이드로 충분하며 파일 삭제 흐름을 건드리지 않는다.)
CREATE TABLE inp_mapping (
    mapping_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '매핑 PK(대리키)',
    inp_file_id     VARCHAR(36)  NOT NULL COMMENT 'INP 파일 ID(마스터 inp_file_m FK)',
    junction_id     VARCHAR(255) NOT NULL COMMENT '절점(junction) ID(INP 노드 ID)',
    pipe_id         VARCHAR(255) NOT NULL COMMENT '관로(pipe) ID(INP 링크 ID)',
    flow_tag_no     VARCHAR(255)          COMMENT '유량태그번호(SCADA 태그, 선택)',
    pressure_tag_no VARCHAR(255)          COMMENT '압력태그번호(SCADA 태그, 선택)',
    point_nm        VARCHAR(255)          COMMENT '지점명(표시용, 선택)',
    rgst_dttm       DATETIME(6)  NOT NULL COMMENT '등록일시',
    mdf_dttm        DATETIME(6)  NOT NULL COMMENT '수정일시',
    PRIMARY KEY (mapping_id),
    UNIQUE KEY uq_mapping_file_junction (inp_file_id, junction_id),
    KEY idx_mapping_file_id (inp_file_id),
    CONSTRAINT fk_mapping_inp_file FOREIGN KEY (inp_file_id)
        REFERENCES inp_file_m (inp_file_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INP 측정 지점 매핑';
