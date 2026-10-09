package com.mo.swtp.auth.api;

import com.mo.swtp.auth.token.IssuedTokens;
import com.mo.swtp.auth.token.TokenService;
import com.mo.swtp.auth.user.User;
import com.mo.swtp.auth.user.UserRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 흐름 — 자격 확인까지가 이 클래스의 책임이고, 토큰 생성은 {@link TokenService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 존재하지 않는 계정에도 해시 비교를 한 번 수행하기 위한 더미 값.
     *
     * <p>계정이 없을 때 즉시 반환하면 응답 시간이 눈에 띄게 짧아져, 공격자가 응답 지연만으로
     * "이 아이디는 존재한다"를 알아낼 수 있다(사용자 열거). BCrypt 비교 비용을 항상 치러 이를 막는다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$sl963JlHLx.w7g9xMbTX..R1oRkn7TRdCvz0AC2vSgmyvwbQIUCZW";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public IssuedTokens login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        String storedHash = (user != null) ? user.getPasswordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, storedHash);

        if (user == null || !user.isEnabled() || !passwordMatches) {
            // 실패 사유(없는 계정/틀린 비밀번호/비활성)를 구분해 알려주지 않는다
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다");
        }
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다"));
    }
}
