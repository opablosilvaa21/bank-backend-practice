package dev.practice.bank.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(Long id, Long accountId, UUID transferId, String message, Instant createdAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getAccountId(), n.getTransferId(), n.getMessage(), n.getCreatedAt());
    }
}
