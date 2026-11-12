package com.agent.smartsecureemail.organization;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Creates the default organization after all beans exist but <em>before</em> the web
 * server starts accepting requests, so registration never races the bootstrap.
 */
@Component
class DefaultOrganizationInitializer implements SmartInitializingSingleton {

    private final OrganizationService organizationService;

    DefaultOrganizationInitializer(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        organizationService.ensureDefaultOrganization();
    }
}
