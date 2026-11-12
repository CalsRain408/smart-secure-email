package com.agent.smartsecureemail.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Startup must fail fast on an invalid domain configuration. */
class MailDomainPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(MailDomainProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void bindsValidConfiguration() {
        runner.withPropertyValues(
                        "ssm.domain.id=domain-a",
                        "ssm.domain.name=domain-a.test",
                        "ssm.domain.display-name=Domain A",
                        "ssm.domain.storage-root=target/x")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    MailDomainProperties props = ctx.getBean(MailDomainProperties.class);
                    assertThat(props.name()).isEqualTo("domain-a.test");
                    assertThat(props.isLocalDomain("DOMAIN-A.TEST")).isTrue();
                    assertThat(props.isLocalDomain("domain-b.test")).isFalse();
                    assertThat(props.isLocalDomain(null)).isFalse();
                });
    }

    @Test
    void rejectsMalformedDomainName() {
        runner.withPropertyValues(
                        "ssm.domain.id=domain-a",
                        "ssm.domain.name=Bad_Domain",
                        "ssm.domain.display-name=Domain A",
                        "ssm.domain.storage-root=target/x")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsPathLikeInstanceId() {
        // The id is used to build local data paths, so traversal sequences must be rejected.
        runner.withPropertyValues(
                        "ssm.domain.id=../domain-b",
                        "ssm.domain.name=domain-a.test",
                        "ssm.domain.display-name=Domain A",
                        "ssm.domain.storage-root=target/x")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsMissingStorageRoot() {
        runner.withPropertyValues(
                        "ssm.domain.id=domain-a",
                        "ssm.domain.name=domain-a.test",
                        "ssm.domain.display-name=Domain A")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
