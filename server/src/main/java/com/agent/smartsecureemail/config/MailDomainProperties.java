package com.agent.smartsecureemail.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identity of the email domain served by this process.
 *
 * <p>Each server instance serves exactly one domain and owns its own database
 * and storage directory. Values come from the active domain profile and may be
 * overridden by environment variables; startup fails if they are invalid.
 *
 * @param id          short instance slug used for local data directories, e.g. {@code domain-a}
 * @param name        fully qualified mail domain, e.g. {@code domain-a.test}
 * @param displayName human readable organization/domain label
 * @param storageRoot root directory for this domain's private files (attachments, etc.)
 */
@Validated
@ConfigurationProperties(prefix = "ssm.domain")
public record MailDomainProperties(
        @NotBlank @Pattern(regexp = ID_PATTERN) String id,
        @NotBlank @Pattern(regexp = DOMAIN_PATTERN) String name,
        @NotBlank String displayName,
        @NotNull Path storageRoot) {

    static final String ID_PATTERN = "^[a-z0-9][a-z0-9-]{0,31}$";

    /** Lowercase DNS-style domain with at least one dot; labels 1-63 chars. */
    static final String DOMAIN_PATTERN =
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}$";

    /** Returns true if the given domain (case-insensitive) is the one served by this instance. */
    public boolean isLocalDomain(String domain) {
        return domain != null && name.equalsIgnoreCase(domain);
    }
}
