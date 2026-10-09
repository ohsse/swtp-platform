package com.mo.swtp.starter.persistence;

import com.mo.swtp.common.audit.AuditorProvider;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 자동 활성화 — {@link BaseCreatedEntity}/{@link BaseEntity}의
 * {@code rgstr_dttm}·{@code rgstr_id}·{@code mdf_dttm}·{@code mdf_id}를 자동으로 채운다.
 *
 * <p>두 겹으로 조건을 건다:
 * <ul>
 *   <li>{@code @ConditionalOnClass} — data-jpa를 의존하지 않는 앱(JdbcClient만 쓰는 telemetry/job)에서는
 *       클래스 자체가 없어 로드되지 않는다</li>
 *   <li>{@code after} + {@code @ConditionalOnBean} — auditing 핸들러는 내부적으로 jpaMappingContext에
 *       의존하므로, JPA 인프라가 구성된 뒤에 켜져야 한다 (클래스패스 조건만으로는 기동이 깨진다)</li>
 * </ul>
 *
 * <p><b>{@code modifyOnCreate}는 기본값 {@code true}를 그대로 쓴다</b> — insert 시점에도 수정 컬럼을 채운다.
 * {@code mdf_dttm}/{@code mdf_id}는 NOT NULL이므로 비워두면 insert 자체가 제약 위반으로 실패한다
 * ({@link BaseEntity} 참고). Spring Data는 등록·수정 값을 같은 시각 인스턴스로 세팅하므로
 * 등록 직후 {@code mdf_dttm}과 {@code rgstr_dttm}은 정확히 같다.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({ EntityManagerFactory.class, AuditorAware.class })
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
public class SwtpJpaAuditingAutoConfiguration {

    /**
     * 플랫폼 유일의 {@link AuditorAware} 빈.
     *
     * <p>{@code auditorAwareRef}를 지정하지 않는 이유: Spring Data는 {@code AuditingHandler}를
     * {@code AUTOWIRE_BY_TYPE}으로 배선하므로 타입만 맞으면 주입된다. 이름을 고정하면
     * 앱이 자기 {@code AuditorAware}로 교체할 때 이름까지 맞춰야 하는 제약이 생긴다.
     */
    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    public AuditorAware<String> swtpAuditorAware(
            ObjectProvider<AuditorProvider> auditorProviders,
            @Value("${swtp.persistence.auditing.system-auditor:SYSTEM}") String systemAuditor) {
        return new SwtpAuditorAware(auditorProviders, systemAuditor);
    }
}
