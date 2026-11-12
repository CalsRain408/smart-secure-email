package com.agent.smartsecureemail.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (federation outbox dispatch, nonce purge). Can be switched off for tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ssm.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
