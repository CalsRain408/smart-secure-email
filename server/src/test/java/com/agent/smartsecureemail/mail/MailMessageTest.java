package com.agent.smartsecureemail.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mail state transitions: only drafts are editable/sendable; sending is one-way. */
class MailMessageTest {

    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static RecipientSpec to(String address) {
        return new RecipientSpec(EmailAddress.parse(address).orElseThrow(), RecipientType.TO);
    }

    private static MailMessage draft() {
        return MailMessage.newLocalDraft("id-1@a.test", EmailAddress.parse("alice@a.test").orElseThrow(),
                "Alice", 1L, T0);
    }

    @Test
    void newDraftStartsEmptyAndUnsent() {
        MailMessage m = draft();

        assertThat(m.getStatus()).isEqualTo(MailStatus.DRAFT);
        assertThat(m.getSentAt()).isNull();
        assertThat(m.getRecipients()).isEmpty();
        assertThat(m.isAuthoredBy(1L)).isTrue();
        assertThat(m.isAuthoredBy(2L)).isFalse();
    }

    @Test
    void sendingDraftSetsSentState() {
        MailMessage m = draft();
        m.editDraft("Hi", "Body", List.of(to("bob@a.test")), T0);

        m.markSent(T1);

        assertThat(m.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(m.getSentAt()).isEqualTo(T1);
    }

    @Test
    void sentMessageCannotBeEditedOrSentAgain() {
        MailMessage m = draft();
        m.markSent(T1);

        assertThatThrownBy(() -> m.editDraft("x", "y", List.of(), T1))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.MAIL_INVALID_STATE));
        assertThatThrownBy(() -> m.markSent(T1)).isInstanceOf(ApiException.class);
    }

    @Test
    void editingDraftSyncsRecipientsInPlace() {
        MailMessage m = draft();
        m.editDraft("s", "b", List.of(to("bob@a.test"), to("carol@a.test")), T0);
        MailRecipient bob = m.getRecipients().get(0);

        m.editDraft("s", "b", List.of(
                new RecipientSpec(EmailAddress.parse("bob@a.test").orElseThrow(), RecipientType.CC),
                to("dave@a.test")), T1);

        assertThat(m.getRecipients()).extracting(MailRecipient::getAddress)
                .containsExactly("bob@a.test", "dave@a.test");
        // The existing row is reused (same object), only its type changes.
        assertThat(m.getRecipients().get(0)).isSameAs(bob);
        assertThat(bob.getRecipientType()).isEqualTo(RecipientType.CC);
    }

    @Test
    void recipientDeliveryIsOneWay() {
        MailMessage m = draft();
        m.editDraft("s", "b", List.of(to("bob@a.test")), T0);
        MailRecipient r = m.getRecipients().get(0);

        r.markDelivered(T1);

        assertThat(r.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThatThrownBy(() -> r.markDelivered(T1)).isInstanceOf(IllegalStateException.class);
    }
}
