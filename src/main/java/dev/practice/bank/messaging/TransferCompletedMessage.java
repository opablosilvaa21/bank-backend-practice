package dev.practice.bank.messaging;

import dev.practice.bank.transfer.TransferChannel;
import dev.practice.bank.transfer.TransferCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato da mensagem publicada em bank.transfers. Quem consome depende deste formato, então ele é versionado:
 * acrescentar um campo é compatível; mudar o significado ou remover um campo exige uma nova versão.
 */
public record TransferCompletedMessage(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        UUID transferId,
        TransferChannel channel,
        Long fromAccountId,
        String fromOwnerName,
        Long toAccountId,
        String toOwnerName,
        BigDecimal amount,
        String currency,
        Instant executedAt
) {
    public static final String TYPE = "TransferCompleted";
    public static final int VERSION = 1;

    public static TransferCompletedMessage of(UUID eventId, TransferCompletedEvent e, Instant occurredAt) {
        return new TransferCompletedMessage(eventId, TYPE, VERSION, occurredAt, e.transferId(), e.channel(),
                e.fromAccountId(), e.fromOwnerName(), e.toAccountId(), e.toOwnerName(), e.amount(), e.currency(),
                e.executedAt());
    }

    public TransferCompletedEvent toEvent() {
        return new TransferCompletedEvent(transferId, channel, fromAccountId, fromOwnerName, toAccountId, toOwnerName,
                amount, currency, executedAt);
    }
}
