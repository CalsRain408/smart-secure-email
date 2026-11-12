package com.agent.smartsecureemail.mail;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** All finders are scoped by owner: there is intentionally no "find by id" for clients. */
public interface MailboxItemRepository extends JpaRepository<MailboxItem, Long> {

    @EntityGraph(attributePaths = "message")
    Optional<MailboxItem> findByIdAndOwnerUserIdAndRecalledFalse(Long id, Long ownerUserId);

    @EntityGraph(attributePaths = "message")
    Page<MailboxItem> findByOwnerUserIdAndFolderAndRecalledFalse(Long ownerUserId, MailFolder folder, Pageable pageable);

    List<MailboxItem> findByMessageId(Long messageId);

    boolean existsByOwnerUserIdAndMessage_IdAndFolder(Long ownerUserId, Long messageId, MailFolder folder);
}
