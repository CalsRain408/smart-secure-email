package com.agent.smartsecureemail.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security tunables.
 *
 * @param passwordHashStrength BCrypt log2 cost. 12 in all real profiles; tests lower it for speed.
 */
@Validated
@ConfigurationProperties(prefix = "ssm.security")
public record SecurityProperties(@Min(4) @Max(16) int passwordHashStrength) {
}
