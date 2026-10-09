package com.mo.swtp.common.api;

/**
 * API 에러 코드 계약 — code + httpStatus 2요소.
 * 에러 메시지는 서버가 내려주지 않는다: 프론트가 코드값 기반 i18n으로 표현을 책임진다.
 * 각 서비스는 자기 도메인 에러를 이 인터페이스를 구현한 enum으로 정의한다.
 *
 * <p>bean 규약(getX) 명명 — 구현체가 Lombok @Getter만으로 계약을 충족하게 한다.
 * swtp-common은 프레임워크 비의존 모듈이므로 HTTP 상태는 int로 표현한다.
 */
public interface ErrorCode {

    /** 응답 봉투의 code 슬롯에 실리는 코드 문자열 (예: "COMMON-404") — 프론트 i18n 키 */
    String getCode();

    /** 매핑되는 HTTP 상태 코드 */
    int getHttpStatus();
}
