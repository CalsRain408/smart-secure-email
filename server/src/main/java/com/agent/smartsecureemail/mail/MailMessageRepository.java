package com.agent.smartsecureemail.mail;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailMessageRepository extends JpaRepository<MailMessage, Long> {

    Optional<MailMessage> findByMessageId(String messageId);
}
