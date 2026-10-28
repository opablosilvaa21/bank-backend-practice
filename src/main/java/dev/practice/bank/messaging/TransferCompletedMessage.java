package dev.practice.bank.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** Lê e confere a mensagem. Qualquer problema vira InvalidMessageException, que vai direto para a DLT. */
    public static TransferCompletedMessage parse(ObjectMapper json, String payload) {
        TransferCompletedMessage message;
        try {
            message = json.readValue(payload, TransferCompletedMessage.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidMessageException("Mensagem não é um JSON válido de " + TYPE, e);
        }
        if (message == null || !TYPE.equals(message.eventType())) {
            throw new InvalidMessageException("Tipo de evento inesperado");
        }
        if (message.version() != VERSION) {
            throw new InvalidMessageException("Versão não suportada: " + message.version());
        }
        if (message.eventId() == null || message.transferId() == null || message.channel() == null
                || message.fromAccountId() == null || message.toAccountId() == null || message.amount() == null) {
            throw new InvalidMessageException("Campo obrigatório ausente na mensagem " + message.eventId());
        }
        return message;
    }

    public TransferCompletedEvent toEvent() {
        return new TransferCompletedEvent(transferId, channel, fromAccountId, fromOwnerName, toAccountId, toOwnerName,
                amount, currency, executedAt);
    }
}
