package com.mo.swtp.starter.persistence;

import java.util.Optional;

import com.mo.swtp.common.audit.AuditorProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.AuditorAware;

/**
 * {@code rgstr_id}/{@code mdf_id}에 넣을 감사 주체를 결정한다.
 *
 * <p>Spring Data가 인식하는 {@link AuditorAware} 빈은 플랫폼 전체에서 이 클래스 하나뿐이고,
 * "현재 사용자가 누구인가"는 {@link AuditorProvider} 구현체(보안 스타터 등)에 런타임 위임한다.
 * 스타터끼리 {@code AuditorAware} 빈을 두고 경쟁하지 않게 하려는 구조다 —
 * 빈이 둘이면 {@code AuditingHandler}의 by-type 배선이 실패한다.
 */
public class SwtpAuditorAware implements AuditorAware<String> {

    /** {@code rgstr_id}/{@code mdf_id} 컬럼 길이 — 초과분은 잘라 넣는다 */
    static final int MAX_AUDITOR_LENGTH = 50;

    private static final Logger log = LoggerFactory.getLogger(SwtpAuditorAware.class);

    private final ObjectProvider<AuditorProvider> providers;
    private final String systemAuditor;

    public SwtpAuditorAware(ObjectProvider<AuditorProvider> providers, String systemAuditor) {
        this.providers = providers;
        this.systemAuditor = truncate(systemAuditor);
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        return providers.orderedStream()
                .map(AuditorProvider::currentAuditor)
                .flatMap(Optional::stream)
                .findFirst()
                .map(SwtpAuditorAware::truncate)
                // 인증 컨텍스트가 없는 경로(스케줄러, Kafka 컨슈머, 마이그레이션)도 반드시 값을 남겨야 한다 —
                // rgstr_id는 NOT NULL이므로 empty를 반환하면 insert 자체가 실패한다.
                .or(() -> Optional.of(systemAuditor));
    }

    private static String truncate(String auditor) {
        if (auditor.length() <= MAX_AUDITOR_LENGTH) {
            return auditor;
        }
        log.warn("감사 주체 식별자가 {}자를 초과해 잘라 기록한다: {}", MAX_AUDITOR_LENGTH, auditor);
        return auditor.substring(0, MAX_AUDITOR_LENGTH);
    }
}
