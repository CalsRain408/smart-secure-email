package com.agent.smartsecureemail.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecipientNormalizerTest {

    @Test
    void deduplicatesKeepingMostVisibleField() {
        List<RecipientSpec> result = RecipientNormalizer.normalize(
                List.of("bob@d.test"),
                List.of("BOB@d.test", "carol@d.test"),
                List.of("carol@d.test", "dave@d.test"));

        assertThat(result).extracting(r -> r.address().value() + ":" + r.type())
                .containsExactly("bob@d.test:TO", "carol@d.test:CC", "dave@d.test:BCC");
    }

    @Test
    void reportsEveryInvalidAddressWithItsPosition() {
        assertThatThrownBy(() -> RecipientNormalizer.normalize(
                List.of("ok@d.test", "not-an-address"), List.of(), List.of("x@")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.details()).extracting(d -> d.field()).containsExactly("to[1]", "bcc[0]");
                });
    }

    @Test
    void enforcesRecipientLimitAfterDeduplication() {
        List<String> fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(i -> "u" + i + "@d.test").toList();

        assertThatThrownBy(() -> RecipientNormalizer.normalize(fiftyOne, List.of(), List.of()))
                .isInstanceOf(ApiException.class);
        // 50 unique addresses repeated across fields is still within the limit.
        List<String> fifty = fiftyOne.subList(0, 50);
        assertThat(RecipientNormalizer.normalize(fifty, fifty, fifty)).hasSize(50);
    }
}
