package com.mo.swtp.starter.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 인증 주체({@link SwtpPrincipal})를 주입한다.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ApiResponse<MeResponse> me(@CurrentUser SwtpPrincipal principal) { ... }
 * }</pre>
 *
 * <p>비인증 요청(permitAll 경로)에서는 {@code null}이 주입된다 — 인증을 강제하는 주체는
 * 이 어노테이션이 아니라 SecurityFilterChain이다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
