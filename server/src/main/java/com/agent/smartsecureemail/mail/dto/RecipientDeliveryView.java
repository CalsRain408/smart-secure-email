package com.agent.smartsecureemail.mail.dto;

import com.agent.smartsecureemail.mail.DeliveryStatus;
import com.agent.smartsecureemail.mail.RecipientType;

public record RecipientDeliveryView(String address, RecipientType type, DeliveryStatus status) {
}
