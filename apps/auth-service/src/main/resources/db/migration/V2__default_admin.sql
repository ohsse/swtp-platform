-- 초기 관리자 계정과 기본 역할.
--
-- ⚠ 개발/데모 기본값이다. 운영 반입 전에 반드시 비밀번호를 변경하거나 이 마이그레이션을 제거하고
--    운영 계정 프로비저닝 절차로 대체한다. (비밀번호: admin123!)
--    평문이 아니라 BCrypt(cost 10) 해시만 저장한다 — 마이그레이션 파일에도 원문은 남기지 않는다.
--
-- 감사 컬럼은 애플리케이션이 채우지만 시드 행은 애플리케이션을 거치지 않으므로 여기서 직접 넣는다.
-- 등록 주체는 'SYSTEM' — 스타터의 AuditorProvider 기본값과 같은 값이라 이력이 일관되게 읽힌다.
-- mdf_dttm/mdf_id는 NOT NULL이므로 등록값과 동일하게 함께 넣는다 (규약: 컬럼이 있으면 값이 반드시 있다).
-- user_roles는 등록 전용 테이블이라 mdf_* 컬럼 자체가 없다.

INSERT INTO roles (role_id, role_name, description, rgstr_dttm, rgstr_id, mdf_dttm, mdf_id) VALUES
    ('ADMIN',    '시스템 관리자', '전체 기능 접근',       LOCALTIMESTAMP, 'SYSTEM', LOCALTIMESTAMP, 'SYSTEM'),
    ('OPERATOR', '운영자',       '운전/제어 기능 접근',   LOCALTIMESTAMP, 'SYSTEM', LOCALTIMESTAMP, 'SYSTEM'),
    ('VIEWER',   '조회자',       '조회 전용',            LOCALTIMESTAMP, 'SYSTEM', LOCALTIMESTAMP, 'SYSTEM');

INSERT INTO users (user_id, username, password_hash, display_name, enabled, rgstr_dttm, rgstr_id, mdf_dttm, mdf_id) VALUES
    ('admin', 'admin', '$2a$10$sl963JlHLx.w7g9xMbTX..R1oRkn7TRdCvz0AC2vSgmyvwbQIUCZW',
     '시스템 관리자', TRUE, LOCALTIMESTAMP, 'SYSTEM', LOCALTIMESTAMP, 'SYSTEM');

INSERT INTO user_roles (user_id, role_id, rgstr_dttm, rgstr_id) VALUES
    ('admin', 'ADMIN', LOCALTIMESTAMP, 'SYSTEM');
