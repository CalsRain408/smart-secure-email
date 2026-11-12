package com.agent.smartsecureemail.federation.protocol;

import com.agent.smartsecureemail.mail.RecipientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Delivery envelope, protocol v1 (docs/protocol.md B.3).
 *
 * <p>{@code recipients} are the addresses the destination must deliver to (including BCC);
 * {@code visibleTo}/{@code visibleCc} are the headers every recipient may see (never BCC).
 */
public record FederationEnvelope(
        @NotNull @Min(1) @Max(1) Integer protocolVersion,
        @NotBlank @Size(max = 255) String messageId,
        @NotNull @Valid Party from,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Recipient> recipients,
        @Size(max = 50) List<@NotNull @Size(max = 320) String> visibleTo,
        @Size(max = 50) List<@NotNull @Size(max = 320) String> visibleCc,
        @NotNull @Size(max = 255) @Pattern(regexp = "^[^\\r\\n\\u0000]*$") String subject,
        @NotNull @Size(max = 100_000) String bodyText,
        @Size(max = 255) String inReplyTo,
        @NotNull Instant sentAt) {

    public List<String> visibleToOrEmpty() {
        return visibleTo == null ? List.of() : visibleTo;
    }

    public List<String> visibleCcOrEmpty() {
        return visibleCc == null ? List.of() : visibleCc;
    }

    public record Party(
            @NotBlank @Size(max = 320) String address,
            @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]*$") String displayName) {
    }

    public record Recipient(@NotBlank @Size(max = 320) String address, @NotNull RecipientType type) {
    }
}
