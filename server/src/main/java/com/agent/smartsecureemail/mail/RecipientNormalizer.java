package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.common.api.ApiError.FieldViolation;
import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses and de-duplicates TO/CC/BCC input. An address listed in several fields keeps the
 * most visible one (TO &gt; CC &gt; BCC), so each address is stored exactly once.
 */
public final class RecipientNormalizer {

    public static final int MAX_RECIPIENTS = 50;

    private RecipientNormalizer() {
    }

    public static List<RecipientSpec> normalize(List<String> to, List<String> cc, List<String> bcc) {
        List<FieldViolation> violations = new ArrayList<>();
        Map<String, RecipientSpec> byAddress = new LinkedHashMap<>();

        collect("to", to, RecipientType.TO, byAddress, violations);
        collect("cc", cc, RecipientType.CC, byAddress, violations);
        collect("bcc", bcc, RecipientType.BCC, byAddress, violations);

        if (byAddress.size() > MAX_RECIPIENTS) {
            violations.add(new FieldViolation("recipients", "at most " + MAX_RECIPIENTS + " recipients allowed"));
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), violations);
        }
        return List.copyOf(byAddress.values());
    }

    private static void collect(String field, List<String> raw, RecipientType type,
                                Map<String, RecipientSpec> byAddress, List<FieldViolation> violations) {
        for (int i = 0; i < raw.size(); i++) {
            Optional<EmailAddress> parsed = EmailAddress.parse(raw.get(i));
            if (parsed.isEmpty()) {
                violations.add(new FieldViolation(field + "[" + i + "]", "invalid mail address"));
                continue;
            }
            // Fields are processed in visibility order, so the first occurrence wins.
            byAddress.putIfAbsent(parsed.get().value(), new RecipientSpec(parsed.get(), type));
        }
    }
}
