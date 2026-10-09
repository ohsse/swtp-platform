package com.mo.swtp.ems.support;

import com.mo.swtp.common.api.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ems-service 도메인 에러 코드 — 앱당 enum 하나(apps/CLAUDE.md).
 *
 * <p>코드 문자열이 프론트 i18n 키이므로 <b>실제로 쓰이는 코드만 넣는다.</b>
 * 쓰이지 않는 키가 먼저 배포되면 프론트에 죽은 번역이 남고 지우기가 어려워진다.
 * 범용 코드(404·400·401 등)는 {@link com.mo.swtp.common.api.CommonErrorCode}를 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum EmsErrorCode implements ErrorCode {

    /**
     * 이미 존재하는 식별자로 등록을 시도했다.
     *
     * <p><b>이 코드가 없으면 등록이 조용한 수정이 된다.</b> 제어그룹·수계통지점의 PK는
     * 애플리케이션이 넣는 assigned-ID이고 {@code Persistable} 구현이 없으므로,
     * Spring Data {@code SimpleJpaRepository.save()}의 {@code isNew()}가 {@code id == null}을 보고
     * 항상 false를 반환해 {@code em.merge()} 경로를 탄다. 즉 기존 ID로 등록을 호출하면
     * 예외가 아니라 UPDATE가 되고, {@code rgstr_dttm}은 {@code updatable = false}라 그대로 남아
     * 등록자만 바뀐 행이 생긴다. 서비스 계층의 사전 존재 검사가 유일한 그물이다.
     */
    DUPLICATE_ID("EMS-409", 409);

    private final String code;
    private final int httpStatus;
}
