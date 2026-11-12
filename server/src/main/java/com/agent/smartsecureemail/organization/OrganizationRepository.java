package com.agent.smartsecureemail.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** The default organization is the first one created for this domain. */
    Optional<Organization> findFirstByOrderByIdAsc();
}
