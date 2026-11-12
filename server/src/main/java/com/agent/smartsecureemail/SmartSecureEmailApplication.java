package com.agent.smartsecureemail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Mail server entry point. One process serves exactly one email domain,
 * selected by Spring profile (e.g. {@code domain-a}, {@code domain-b}).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Spring Boot never
 * generates and logs a default in-memory password. Real user authentication is
 * provided by the auth module (Phase 2).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SmartSecureEmailApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSecureEmailApplication.class, args);
    }
}
