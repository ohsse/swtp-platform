package com.mo.swtp.starter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mo.swtp.common.audit.AuditorProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 주체 결정 규칙 검증 — rgstr_id/mdf_id는 NOT NULL이므로 "값이 없는 경우"가 있어선 안 된다.
 */
class SwtpAuditorAwareTest {

    @Test
    @DisplayName("AuditorProvider가 하나도 없으면 시스템 기본값을 쓴다 — 스케줄러/컨슈머 경로 보호")
    void fallsBackToSystemAuditor() {
        var auditorAware = new SwtpAuditorAware(providersOf(), "SYSTEM");

        assertThat(auditorAware.getCurrentAuditor()).contains("SYSTEM");
    }

    @Test
    @DisplayName("값을 반환하는 첫 Provider가 이긴다 — 비인증 요청은 empty를 반환해 다음으로 넘긴다")
    void firstNonEmptyProviderWins() {
        var auditorAware = new SwtpAuditorAware(
                providersOf(Optional::empty, () -> Optional.of("swtp-admin"), () -> Optional.of("무시됨")),
                "SYSTEM");

        assertThat(auditorAware.getCurrentAuditor()).contains("swtp-admin");
    }

    @Test
    @DisplayName("컬럼 길이(50)를 넘는 식별자는 잘라 넣는다 — insert 실패로 업무가 멈추지 않게")
    void truncatesOverlongAuditor() {
        String tooLong = "x".repeat(80);
        var auditorAware = new SwtpAuditorAware(providersOf(() -> Optional.of(tooLong)), "SYSTEM");

        assertThat(auditorAware.getCurrentAuditor())
                .hasValueSatisfying(value -> assertThat(value).hasSize(SwtpAuditorAware.MAX_AUDITOR_LENGTH));
    }

    /** stream()만 구현하면 orderedStream()은 기본 구현이 OrderComparator를 적용해 처리한다 */
    private static ObjectProvider<AuditorProvider> providersOf(AuditorProvider... providers) {
        List<AuditorProvider> beans = List.of(providers);
        return new ObjectProvider<>() {
            @Override
            public AuditorProvider getObject() {
                return beans.getFirst();
            }

            @Override
            public Stream<AuditorProvider> stream() {
                return beans.stream();
            }
        };
    }
}
