package com.mo.swtp.starter.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentUser SwtpPrincipal} 파라미터를 SecurityContext에서 채운다.
 *
 * <p>{@code @AuthenticationPrincipal} 대신 전용 리졸버를 두는 이유: 표준 어노테이션이 주입하는 것은
 * {@code Jwt} 원본이라, 클레임 파싱이 컨트롤러마다 흩어진다. 여기서 한 번만 {@link SwtpPrincipal}로
 * 변환해 두면 토큰 포맷 변경의 영향 범위가 스타터 안에 갇힌다.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && SwtpPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // 비인증 요청에서는 null — 인증 강제는 SecurityFilterChain의 책임이지 리졸버의 책임이 아니다
        return SwtpSecurityContext.currentPrincipal().orElse(null);
    }
}
