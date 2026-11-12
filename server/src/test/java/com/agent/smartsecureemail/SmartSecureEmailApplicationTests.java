package com.agent.smartsecureemail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmartSecureEmailApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywayCreatesBaselineSchema() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "organizations", "users", "mail_messages", "mail_recipients",
                "mailbox_items", "security_audit_events");
    }

    @Test
    void schemaRejectsInvalidEnumValues() {
        jdbcTemplate.update("INSERT INTO organizations (name, created_at) VALUES ('org-ck', CURRENT_TIMESTAMP)");
        Long orgId = jdbcTemplate.queryForObject(
                "SELECT id FROM organizations WHERE name = 'org-ck'", Long.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (organization_id, username, display_name, password_hash, role, status,
                                   created_at, updated_at)
                VALUES (?, 'mallory', 'Mallory', 'x', 'SUPERUSER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, orgId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
