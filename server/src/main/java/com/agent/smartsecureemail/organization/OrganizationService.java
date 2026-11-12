package com.agent.smartsecureemail.organization;

import com.agent.smartsecureemail.config.MailDomainProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository repository;
    private final MailDomainProperties domain;
    private final Clock clock;

    public OrganizationService(OrganizationRepository repository, MailDomainProperties domain, Clock clock) {
        this.repository = repository;
        this.domain = domain;
        this.clock = clock;
    }

    /** Idempotently creates the domain's default organization. Called once at startup. */
    @Transactional
    public Organization ensureDefaultOrganization() {
        return repository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Organization created = repository.save(Organization.create(domain.displayName(), Instant.now(clock)));
            log.info("Created default organization id={} for domain={}", created.getId(), domain.name());
            return created;
        });
    }

    @Transactional(readOnly = true)
    public Organization defaultOrganization() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Default organization has not been initialized"));
    }
}
