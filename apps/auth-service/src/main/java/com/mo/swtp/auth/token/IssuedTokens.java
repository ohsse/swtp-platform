package com.mo.swtp.auth.token;

import java.util.List;

/**
 * 발급 결과 한 벌.
 *
 * @param accessToken       API 호출용 서명 토큰
 * @param refreshToken      재발급용 원문 — 이 순간 이후 서버 어디에도 원문은 남지 않는다(해시만 저장)
 * @param expiresInSeconds  액세스 토큰 남은 유효기간
 * @param roles             발급 시점의 역할 — 클라이언트 화면 제어용 참고값
 */
public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds, List<String> roles) {
}
