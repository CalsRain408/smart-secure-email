package com.agent.smartsecureemail.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the domain-private storage directory exists and is writable at startup.
 * Fails fast instead of discovering a broken storage location on first upload.
 */
@Component
public class DomainStorageInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainStorageInitializer.class);

    private final MailDomainProperties domain;

    public DomainStorageInitializer(MailDomainProperties domain) {
        this.domain = domain;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path root = domain.storageRoot().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create domain storage root: " + root, e);
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("Domain storage root is not a writable directory: " + root);
        }
        log.info("Mail domain ready: domain={} instance={} storageRoot={}", domain.name(), domain.id(), root);
    }
}
